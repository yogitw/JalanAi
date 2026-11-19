package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.MaintenanceStatus
import com.example.data.model.RoadInspection
import com.example.data.model.SeiRokanCatalog
import com.example.ui.components.InspectionSummaryCard
import com.example.ui.theme.StatusCritical
import com.example.ui.theme.StatusFair
import com.example.ui.theme.StatusGood
import com.example.ui.viewmodel.InspectionsUiState

@Composable
fun DashboardScreen(
    inspectionsState: InspectionsUiState,
    isOnline: Boolean,
    unsyncedLocalCount: Int,
    isSyncing: Boolean,
    onSyncLocalToCloud: () -> Unit,
    onNavigateToAnalyze: () -> Unit,
    onNavigateToSchedule: () -> Unit,
    onOpenDetail: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        when (inspectionsState) {
            is InspectionsUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Memuat data pengawasan jalan...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            is InspectionsUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = inspectionsState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            is InspectionsUiState.Success -> {
                val allItems = inspectionsState.items
                var selectedAfdelingFilter by rememberSaveable { mutableStateOf<String?>(null) }
                val filteredItems = remember(allItems, selectedAfdelingFilter) {
                    val filterName = selectedAfdelingFilter
                    if (filterName == null) {
                        allItems
                    } else {
                        allItems.filter { item ->
                            item.segmentLocation.contains("$filterName ", ignoreCase = true) ||
                                item.segmentLocation.endsWith(filterName, ignoreCase = true) ||
                                item.roadName.contains(filterName, ignoreCase = true)
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .widthIn(max = 720.dp)
                        .fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        DashboardHeroBanner(
                            onNavigateToAnalyze = onNavigateToAnalyze
                        )
                    }

                    if (unsyncedLocalCount > 0) {
                        item {
                            LocalSyncBannerCard(
                                unsyncedCount = unsyncedLocalCount,
                                isOnline = isOnline,
                                isSyncing = isSyncing,
                                onSyncClick = onSyncLocalToCloud
                            )
                        }
                    }

                    item {
                        KpiOverviewGrid(
                            items = allItems,
                            onNavigateToSchedule = onNavigateToSchedule
                        )
                    }

                    if (allItems.isEmpty()) {
                        item {
                            EmptyInspectionsCard(onNavigateToAnalyze = onNavigateToAnalyze)
                        }
                    } else {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Riwayat Kebun Sei Rokan (${filteredItems.size})",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.CloudDone,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                text = "Cloud Bersama",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    item {
                                        FilterChip(
                                            selected = selectedAfdelingFilter == null,
                                            onClick = { selectedAfdelingFilter = null },
                                            label = { Text("Semua (${allItems.size})") }
                                        )
                                    }
                                    items(SeiRokanCatalog.afdelings, key = { it.code }) { afd ->
                                        FilterChip(
                                            selected = selectedAfdelingFilter == afd.displayName,
                                            onClick = { selectedAfdelingFilter = afd.displayName },
                                            label = { Text(afd.displayName) }
                                        )
                                    }
                                }
                            }
                        }

                        items(filteredItems, key = { it.id }) { inspection ->
                            InspectionSummaryCard(
                                inspection = inspection,
                                onClick = { onOpenDetail(inspection.id) }
                            )
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardHeroBanner(
    onNavigateToAnalyze: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF0F2942),
                            Color(0xFF1E3A5F)
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    color = Color(0x33F59E0B),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "KEBUN SEI ROKAN • DATABASE CLOUD BERSAMA",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFFBBF24),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                Text(
                    text = "Deteksi Kerusakan & Jadwal Preventif",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White
                )
                Text(
                    text = "Pindai jalan angkut TBS Afdeling I–VIII Kebun Sei Rokan untuk menilai skor PCI, memprediksi degradasi 12 bulan, dan menjadwalkan perbaikan di bulan kering.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFCBD5E1)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Button(
                    onClick = onNavigateToAnalyze,
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("dashboard_start_analysis_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFF59E0B),
                        contentColor = Color(0xFF0F172A)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Videocam,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Pindai Video Jalan Sekarang",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun LocalSyncBannerCard(
    unsyncedCount: Int,
    isOnline: Boolean,
    isSyncing: Boolean,
    onSyncClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.CloudUpload,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$unsyncedCount Antrean Belum Terunggah",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = if (isOnline) {
                        "Sedang diunggah ke Database Bersama agar terlihat oleh semua APK."
                    } else {
                        "Menunggu sinyal internet untuk otomatis masuk ke Database Bersama."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            OutlinedButton(
                onClick = onSyncClick,
                enabled = isOnline && !isSyncing,
                modifier = Modifier.testTag("sync_local_to_cloud_button"),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(if (isSyncing) "Menyinkronkan..." else "Sinkronkan")
            }
        }
    }
}

@Composable
private fun KpiOverviewGrid(
    items: List<RoadInspection>,
    onNavigateToSchedule: () -> Unit
) {
    val avgPci = if (items.isEmpty()) 0 else items.map { it.pciScore }.average().toInt()
    val priorityCount = items.count { it.pciScore < 55 }
    val scheduledCount = items.count {
        MaintenanceStatus.safeValueOf(it.maintenanceStatus) != MaintenanceStatus.SELESAI
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            KpiCard(
                title = "Ruas Dipantau",
                value = items.size.toString(),
                subtitle = "Rekaman video",
                icon = Icons.Filled.Assessment,
                accentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            KpiCard(
                title = "Rata-rata PCI",
                value = if (items.isEmpty()) "-" else "$avgPci/100",
                subtitle = if (avgPci >= 75) "Kondisi Baik" else if (items.isEmpty()) "Belum ada data" else "Perlu Pantauan",
                icon = Icons.Filled.Speed,
                accentColor = if (avgPci >= 75) StatusGood else StatusFair,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            KpiCard(
                title = "Prioritas Tinggi",
                value = priorityCount.toString(),
                subtitle = "Skor PCI < 55",
                icon = Icons.Filled.WarningAmber,
                accentColor = if (priorityCount > 0) StatusCritical else StatusGood,
                modifier = Modifier.weight(1f)
            )
            KpiCard(
                title = "Jadwal Aktif",
                value = scheduledCount.toString(),
                subtitle = "Lihat kalender",
                icon = Icons.Filled.CalendarMonth,
                accentColor = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.weight(1f),
                onClick = onNavigateToSchedule
            )
        }
    }
}

@Composable
private fun KpiCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        onClick = onClick ?: {}
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = accentColor
            )
        }
    }
}

@Composable
private fun EmptyInspectionsCard(onNavigateToAnalyze: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_empty_inspection),
                contentDescription = stringResource(R.string.cd_empty_illustration),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .clip(RoundedCornerShape(14.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Belum Ada Inspeksi Jalan",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Unggah rekaman video CCTV atau dashcam jalan untuk mengidentifikasi kerusakan aspal dan menyusun jadwal pemeliharaan preventif.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onNavigateToAnalyze,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("empty_state_analyze_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Filled.AddCircle, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Mulai Analisis Video Pertama")
            }
        }
    }
}
