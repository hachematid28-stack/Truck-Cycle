package com.example.ui.screens

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.database.TripEntity
import com.example.data.model.Constants
import com.example.ui.localization.AppLanguage
import com.example.ui.localization.L10n
import com.example.ui.localization.L10nKey
import com.example.ui.theme.*
import com.example.ui.viewmodel.TruckCycleViewModel
import com.example.utils.ExcelExporter
import com.example.utils.PdfExporter
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    viewModel: TruckCycleViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lang by viewModel.appLanguage.collectAsState()
    val allTrips by viewModel.allTrips.collectAsState()

    // Filters State
    var selectedDriverFilter by remember { mutableStateOf("") }
    var selectedTruckFilter by remember { mutableStateOf("") }
    
    // "Daily" (Today), "Weekly" (Last 7 days), "All"
    var selectedTimeSpan by remember { mutableStateOf("Daily") } 

    var driverDropdownExpanded by remember { mutableStateOf(false) }
    var truckDropdownExpanded by remember { mutableStateOf(false) }

    // Derive unique drivers list from all trips
    val uniqueDrivers = remember(allTrips) {
        listOf("") + allTrips.map { it.driverName }.distinct()
    }

    // Filtered list of trips
    val filteredTrips = remember(allTrips, selectedDriverFilter, selectedTruckFilter, selectedTimeSpan) {
        allTrips.filter { trip ->
            val matchDriver = selectedDriverFilter.isEmpty() || trip.driverName.equals(selectedDriverFilter, ignoreCase = true)
            val matchTruck = selectedTruckFilter.isEmpty() || trip.truck.equals(selectedTruckFilter, ignoreCase = true)
            
            val matchTime = when (selectedTimeSpan) {
                "Daily" -> {
                    val tripTime = trip.endUnloadTime ?: trip.startLoadTime ?: trip.waitingStart ?: 0L
                    isSameDay(tripTime, System.currentTimeMillis())
                }
                "Weekly" -> {
                    val tripTime = trip.endUnloadTime ?: trip.startLoadTime ?: trip.waitingStart ?: 0L
                    val sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
                    tripTime >= sevenDaysAgo
                }
                else -> true // "All"
            }

            matchDriver && matchTruck && matchTime
        }
    }

    // Metrics calculations
    val totalTripsCount = filteredTrips.size
    
    val metrics = remember(filteredTrips) {
        var totalWaiting: Long = 0
        var totalBreakdown: Long = 0
        var earliestStart: Long = Long.MAX_VALUE
        var latestEnd: Long = Long.MIN_VALUE

        for (t in filteredTrips) {
            val w = (t.waitingEnd ?: 0L) - (t.waitingStart ?: 0L)
            val wu = (t.startUnloadTime ?: 0L) - (t.arrivalUnloadTime ?: 0L)
            totalWaiting += if (w > 0) w else 0
            totalWaiting += if (wu > 0) wu else 0

            val b = (t.breakdownEnd ?: 0L) - (t.breakdownStart ?: 0L)
            totalBreakdown += if (b > 0) b else 0

            val start = t.waitingStart ?: t.startLoadTime ?: 0L
            if (start in 1..<earliestStart) {
                earliestStart = start
            }
            val end = t.endUnloadTime ?: t.breakdownEnd ?: 0L
            if (end > latestEnd) {
                latestEnd = end
            }
        }

        val totalSpan = if (earliestStart != Long.MAX_VALUE && latestEnd != Long.MIN_VALUE && latestEnd > earliestStart) {
            latestEnd - earliestStart
        } else {
            totalTripsCount * 30 * 60 * 1000L // 30 mins fallback per trip
        }

        val totalWorking = maxOf(0L, totalSpan - totalBreakdown)
        val productivity = if (totalWorking > 0) {
            val productiveTime = maxOf(0L, totalWorking - totalWaiting)
            ((productiveTime.toFloat() / totalWorking.toFloat()) * 100).toInt().coerceIn(0, 100)
        } else {
            100
        }

        object {
            val waiting = totalWaiting
            val breakdown = totalBreakdown
            val working = totalWorking
            val productivityPercentage = productivity
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Filtering Bar
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Time span switcher
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val times = listOf("Daily", "Weekly", "All")
                    times.forEach { t ->
                        val localizedT = when (t) {
                            "Daily" -> L10n.get(L10nKey.DAILY_REPORT, lang)
                            "Weekly" -> L10n.get(L10nKey.WEEKLY_REPORT, lang)
                            else -> "All"
                        }
                        
                        FilterChip(
                            selected = selectedTimeSpan == t,
                            onClick = { selectedTimeSpan = t },
                            label = { Text(localizedT) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Dropdown Filters Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Driver Filter
                    Box(modifier = Modifier.weight(1f)) {
                        Button(
                            onClick = { driverDropdownExpanded = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary,
                                contentColor = MaterialTheme.colorScheme.onSecondary
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (selectedDriverFilter.isEmpty()) L10n.get(L10nKey.DRIVER_NAME, lang) else selectedDriverFilter,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }

                        DropdownMenu(
                            expanded = driverDropdownExpanded,
                            onDismissRequest = { driverDropdownExpanded = false },
                            modifier = Modifier
                                .fillMaxWidth(0.45f)
                                .heightIn(max = 200.dp)
                                .background(MaterialTheme.colorScheme.surface)
                        ) {
                            uniqueDrivers.forEach { driver ->
                                DropdownMenuItem(
                                    text = { Text(driver.ifEmpty { "All" }, color = MaterialTheme.colorScheme.onSurface) },
                                    onClick = {
                                        selectedDriverFilter = driver
                                        driverDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Truck Filter
                    Box(modifier = Modifier.weight(1f)) {
                        Button(
                            onClick = { truckDropdownExpanded = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary,
                                contentColor = MaterialTheme.colorScheme.onSecondary
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (selectedTruckFilter.isEmpty()) L10n.get(L10nKey.TRUCK_MATRICULE, lang) else selectedTruckFilter,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }

                        DropdownMenu(
                            expanded = truckDropdownExpanded,
                            onDismissRequest = { truckDropdownExpanded = false },
                            modifier = Modifier
                                .fillMaxWidth(0.45f)
                                .heightIn(max = 200.dp)
                                .background(MaterialTheme.colorScheme.surface)
                        ) {
                            (listOf("") + Constants.TRUCKS).forEach { truck ->
                                DropdownMenuItem(
                                    text = { Text(truck.ifEmpty { "All" }, color = MaterialTheme.colorScheme.onSurface) },
                                    onClick = {
                                        selectedTruckFilter = truck
                                        truckDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Metrics top summary cards
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Total trips
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(100.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.TrendingUp, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = L10n.get(L10nKey.TOTAL_TRIPS, lang),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Text(
                                text = totalTripsCount.toString(),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Productivity %
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(100.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.Speed, contentDescription = null, tint = SkyBlue)
                            Text(
                                text = L10n.get(L10nKey.PRODUCTIVITY_PERCENTAGE, lang),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "${metrics.productivityPercentage}%",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = SkyBlue
                            )
                        }
                    }
                }
            }

            // Waiting & Breakdown times
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Total Waiting
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(100.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.HourglassEmpty, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                            Text(
                                text = L10n.get(L10nKey.TOTAL_WAITING_TIME, lang),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = Constants.formatDuration(metrics.waiting),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Total Breakdown
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(100.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.Build, contentDescription = null, tint = SafetyRed)
                            Text(
                                text = L10n.get(L10nKey.TOTAL_BREAKDOWN_TIME, lang),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = Constants.formatDuration(metrics.breakdown),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = SafetyRed
                            )
                        }
                    }
                }
            }

            // Export Actions Bar
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                PdfExporter.exportAndSharePdf(
                                    context = context,
                                    trips = filteredTrips,
                                    language = lang,
                                    selectedDriver = selectedDriverFilter,
                                    selectedTruck = selectedTruckFilter,
                                    selectedShift = selectedTimeSpan
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("export_pdf_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(20.dp))
                                Text(L10n.get(L10nKey.EXPORT_PDF, lang), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }

                        Button(
                            onClick = {
                                ExcelExporter.exportAndShareExcel(
                                    context = context,
                                    trips = filteredTrips,
                                    selectedDriver = selectedDriverFilter,
                                    selectedTruck = selectedTruckFilter,
                                    selectedShift = selectedTimeSpan
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SafetyGreen, contentColor = IndustrialWhite),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("export_excel_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.GridOn, contentDescription = null, modifier = Modifier.size(20.dp))
                                Text(L10n.get(L10nKey.EXPORT_EXCEL, lang), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Individual Trip History List acting as "Report Detail Screen"
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "DETAILED CYCLES LIST",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            if (filteredTrips.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = L10n.get(L10nKey.NO_DATA, lang),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                        )
                    }
                }
            } else {
                items(filteredTrips) { trip ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Header: Trip ID, Truck, Shift, Delete Button
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.LocalShipping,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${trip.truck} • ${trip.shift}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                IconButton(
                                    onClick = { viewModel.deleteTrip(trip) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete record",
                                        tint = SafetyRed,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))

                            // Driver info
                            Text(
                                text = "${L10n.get(L10nKey.DRIVER_NAME, lang)}: ${trip.driverName}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                            )

                            // Details of loads/dumps
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "📍 LOAD AREA",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "M: ${trip.machine}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    // Start / End Loading
                                    val loadStartStr = trip.startLoadTime?.let { formatTimeOnly(it) } ?: "N/A"
                                    val loadEndStr = trip.endLoadTime?.let { formatTimeOnly(it) } ?: "N/A"
                                    Text(
                                        text = "$loadStartStr ➔ $loadEndStr",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "📍 DUMP AREA",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SkyBlue
                                    )
                                    Text(
                                        text = "DP: ${trip.dumpPoint}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    val unloadStartStr = trip.startUnloadTime?.let { formatTimeOnly(it) } ?: "N/A"
                                    val unloadEndStr = trip.endUnloadTime?.let { formatTimeOnly(it) } ?: "N/A"
                                    Text(
                                        text = "$unloadStartStr ➔ $unloadEndStr",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                }
                            }

                            // Dynamic phase values
                            val loadDur = if (trip.endLoadTime != null && trip.startLoadTime != null) trip.endLoadTime - trip.startLoadTime else 0L
                            val waitDur = if (trip.waitingEnd != null && trip.waitingStart != null) trip.waitingEnd - trip.waitingStart else 0L
                            val unlWaitDur = if (trip.startUnloadTime != null && trip.arrivalUnloadTime != null) trip.startUnloadTime - trip.arrivalUnloadTime else 0L
                            val breakdownDur = if (trip.breakdownEnd != null && trip.breakdownStart != null) trip.breakdownEnd - trip.breakdownStart else 0L

                            Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "⏱️ DURATION BREAKDOWNS",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                    )
                                    Text(
                                        text = "Load: ${Constants.formatDuration(loadDur)} | Wait Before Load: ${Constants.formatDuration(waitDur)}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                    Text(
                                        text = "Wait At Unload: ${Constants.formatDuration(unlWaitDur)}" +
                                            if (breakdownDur > 0) " | Breakdown: ${Constants.formatDuration(breakdownDur)} (${trip.breakdownType})" else "",
                                        fontSize = 11.sp,
                                        color = if (breakdownDur > 0) SafetyRed else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Utility to verify if two timestamps represent the same day
private fun isSameDay(time1: Long, time2: Long): Boolean {
    if (time1 <= 0 || time2 <= 0) return false
    val cal1 = Calendar.getInstance().apply { timeInMillis = time1 }
    val cal2 = Calendar.getInstance().apply { timeInMillis = time2 }
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
           cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}

// Format time only as HH:mm
private fun formatTimeOnly(time: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(time))
}
