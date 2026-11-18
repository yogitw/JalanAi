package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.MaintenanceStatus
import com.example.data.model.RoadInspection
import com.example.ui.components.PciRingGauge
import com.example.ui.components.statusColor
import com.example.ui.viewmodel.InspectionsUiState

@Composable
fun MaintenanceScheduleScreen(
    inspectionsState: InspectionsUiState,
    onUpdateSchedule: (inspectionId: String, status: MaintenanceStatus, targetMonth: String, notes: String) -> Unit,
    onOpenDetail: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedFilter by remember { mutableStateOf<MaintenanceStatus?>(null) }
    var editingInspection by remember { mutableStateOf<RoadInspection?>(null) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        when (inspectionsState) {
            is InspectionsUiState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            is InspectionsUiState.Error -> {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(inspectionsState.message, color = MaterialTheme.colorScheme.error)
                }
            }

            is InspectionsUiState.Success -> {
                val filtered = inspectionsState.items.filter { item ->
                    selectedFilter == null || MaintenanceStatus.safeValueOf(item.maintenanceStatus) == selectedFilter
                }
                val groupedByMonth = filtered.groupBy { it.optimalMaintenanceMonth.ifBlank { "Belum Ditentukan" } }

                LazyColumn(
                    modifier = Modifier
                        .widthIn(max = 720.dp)
                        .fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Kalender Pemeliharaan Preventif",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Jadwal perbaikan bulanan berdasarkan prediksi degradasi kualitas jalan AI.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                item {
                                    FilterChip(
                                        selected = selectedFilter == null,
                                        onClick = { selectedFilter = null },
                                        label = { Text("Semua (${inspectionsState.items.size})") }
                                    )
                                }
                                items(MaintenanceStatus.entries) { status ->
                                    FilterChip(
                                        selected = selectedFilter == status,
                                        onClick = { selectedFilter = status },
                                        label = { Text(status.label) }
                                    )
                                }
                            }
                        }
                    }

                    if (groupedByMonth.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.CalendarMonth,
                                        contentDescription = null,
                                        modifier = Modifier.size(40.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.height(10.dp))
                                    Text(
                                        text = "Belum ada jadwal untuk filter ini",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Analisis rekaman video jalan di tab Analisis untuk menghasilkan jadwal pemeliharaan preventif otomatis.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        groupedByMonth.forEach { (monthLabel, monthItems) ->
                            item(key = "header_$monthLabel") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.CalendarMonth,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                text = monthLabel,
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.onPrimary
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = "${monthItems.size} segmen jalan",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            items(monthItems, key = { it.id }) { item ->
                                ScheduleItemCard(
                                    inspection = item,
                                    onEditClick = { editingInspection = item },
                                    onOpenDetail = { onOpenDetail(item.id) }
                                )
                            }
                        }
                    }

                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }

    editingInspection?.let { target ->
        EditScheduleDialog(
            inspection = target,
            onDismiss = { editingInspection = null },
            onConfirm = { newStatus, newMonth, newNotes ->
                onUpdateSchedule(target.id, newStatus, newMonth, newNotes)
                editingInspection = null
            }
        )
    }
}

@Composable
private fun ScheduleItemCard(
    inspection: RoadInspection,
    onEditClick: () -> Unit,
    onOpenDetail: () -> Unit
) {
    val status = MaintenanceStatus.safeValueOf(inspection.maintenanceStatus)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("schedule_card_${inspection.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        onClick = onOpenDetail
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PciRingGauge(pciScore = inspection.pciScore.toInt(), sizeDp = 52)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = inspection.roadName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = inspection.preventiveMethod,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = statusColor(status).copy(alpha = 0.14f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = status.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor(status),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Text(
                        text = inspection.urgencyWindow,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (inspection.maintenanceNotes.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Catatan: ${inspection.maintenanceNotes}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            IconButton(
                onClick = onEditClick,
                modifier = Modifier.testTag("edit_schedule_btn_${inspection.id}")
            ) {
                Icon(
                    imageVector = Icons.Filled.EditCalendar,
                    contentDescription = "Ubah jadwal pemeliharaan"
                )
            }
        }
    }
}

@Composable
private fun EditScheduleDialog(
    inspection: RoadInspection,
    onDismiss: () -> Unit,
    onConfirm: (MaintenanceStatus, String, String) -> Unit
) {
    var status by remember { mutableStateOf(MaintenanceStatus.safeValueOf(inspection.maintenanceStatus)) }
    var targetMonth by remember { mutableStateOf(inspection.optimalMaintenanceMonth) }
    var notes by remember { mutableStateOf(inspection.maintenanceNotes) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Perbarui Jadwal Preventif") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = inspection.roadName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MaintenanceStatus.entries.forEach { st ->
                        FilterChip(
                            selected = status == st,
                            onClick = { status = st },
                            label = { Text(st.label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = targetMonth,
                    onValueChange = { targetMonth = it },
                    label = { Text("Bulan Target Perbaikan") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Catatan Tim Lapangan") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(status, targetMonth, notes) },
                enabled = targetMonth.trim().length >= 3
            ) {
                Text("Simpan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}
