package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.example.ui.viewmodel.TripPhase
import com.example.ui.viewmodel.TruckCycleViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: TruckCycleViewModel,
    modifier: Modifier = Modifier
) {
    val lang by viewModel.appLanguage.collectAsState()
    val driverName by viewModel.driverName.collectAsState()
    val truckMatricule by viewModel.truckMatricule.collectAsState()
    val activeShift by viewModel.activeShift.collectAsState()
    
    val activeTrip by viewModel.activeTrip.collectAsState()
    val tripPhase by viewModel.tripPhase.collectAsState()
    val isBreakdownActive by viewModel.isBreakdownActive.collectAsState()
    val lastCompletedTrip by viewModel.lastCompletedTrip.collectAsState()
    val currentTimestamp by viewModel.currentTimestamp.collectAsState()
    
    val allTrips by viewModel.allTrips.collectAsState()

    // Dialog trigger states
    var showMachineDialog by remember { mutableStateOf(false) }
    var showDumpDialog by remember { mutableStateOf(false) }
    var showBreakdownDialog by remember { mutableStateOf(false) }

    // Filter today's trips
    val driversTrips = remember(allTrips, driverName) {
        allTrips.filter { it.driverName == driverName }
    }

    // Dynamic warning flashing animation for breakdown state
    val infiniteTransition = rememberInfiniteTransition(label = "warning_flashing")
    val flashingAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "flashing_alpha"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 1. Session Header
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = driverName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.LocalShipping,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "$truckMatricule • ${
                                when (activeShift) {
                                    "Morning" -> L10n.get(L10nKey.MORNING, lang)
                                    "Afternoon" -> L10n.get(L10nKey.AFTERNOON, lang)
                                    else -> L10n.get(L10nKey.NIGHT, lang)
                                }
                            }",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }

                IconButton(
                    onClick = { viewModel.logout() },
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.secondary, CircleShape)
                        .testTag("logout_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ExitToApp,
                        contentDescription = L10n.get(L10nKey.LOGOUT, lang),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        // 2. Active Flashing Breakdown Banner
        if (isBreakdownActive) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SafetyRed.copy(alpha = flashingAlpha))
                    .border(2.dp, SafetyRed, RoundedCornerShape(12.dp))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Warning",
                        tint = IndustrialWhite,
                        modifier = Modifier.size(28.dp)
                    )
                    Column {
                        Text(
                            text = L10n.get(L10nKey.ACTIVE_BREAKDOWN_FLASHING, lang),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = IndustrialWhite
                        )
                        val bdType = activeTrip?.breakdownType ?: ""
                        val bdStart = activeTrip?.breakdownStart ?: 0L
                        val elapsedBd = if (bdStart > 0) currentTimestamp - bdStart else 0L
                        Text(
                            text = "$bdType • ${L10n.get(L10nKey.DURATION, lang)}: ${Constants.formatDurationExtended(elapsedBd)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = IndustrialWhite.copy(alpha = 0.9f)
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Button(
                        onClick = { viewModel.endBreakdown() },
                        colors = ButtonDefaults.buttonColors(containerColor = IndustrialWhite, contentColor = SafetyRed),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = L10n.get(L10nKey.END_BREAKDOWN, lang),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
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
            // 3. Status Display Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = L10n.get(L10nKey.ACTIVE_SHIFT, lang).uppercase(),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        // Dynamic status text
                        val statusText = when {
                            isBreakdownActive -> L10n.get(L10nKey.BREAKDOWN_MODULE, lang)
                            tripPhase == TripPhase.WAITING_BEFORE_LOADING -> L10n.get(L10nKey.WAITING_BEFORE_LOADING, lang)
                            tripPhase == TripPhase.LOADING -> L10n.get(L10nKey.LOAD_CYCLE, lang) + " (${activeTrip?.machine})"
                            tripPhase == TripPhase.TRANSIT -> L10n.get(L10nKey.STATUS_TRANSIT, lang)
                            tripPhase == TripPhase.WAITING_BEFORE_UNLOADING -> L10n.get(L10nKey.STATUS_WAITING_UNLOAD, lang)
                            tripPhase == TripPhase.UNLOADING -> L10n.get(L10nKey.STATUS_UNLOADING, lang) + " (${activeTrip?.dumpPoint})"
                            tripPhase == TripPhase.RETURN -> L10n.get(L10nKey.STATUS_RETURN, lang)
                            else -> L10n.get(L10nKey.STATUS_IDLE, lang)
                        }

                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Live duration counter
                        val activeDuration = remember(tripPhase, activeTrip, currentTimestamp) {
                            when (tripPhase) {
                                TripPhase.WAITING_BEFORE_LOADING -> {
                                    val start = activeTrip?.waitingStart ?: 0L
                                    if (start > 0) currentTimestamp - start else 0L
                                }
                                TripPhase.LOADING -> {
                                    val start = activeTrip?.startLoadTime ?: 0L
                                    if (start > 0) currentTimestamp - start else 0L
                                }
                                TripPhase.TRANSIT -> {
                                    val start = activeTrip?.endLoadTime ?: 0L
                                    if (start > 0) currentTimestamp - start else 0L
                                }
                                TripPhase.WAITING_BEFORE_UNLOADING -> {
                                    val start = activeTrip?.arrivalUnloadTime ?: 0L
                                    if (start > 0) currentTimestamp - start else 0L
                                }
                                TripPhase.UNLOADING -> {
                                    val start = activeTrip?.startUnloadTime ?: 0L
                                    if (start > 0) currentTimestamp - start else 0L
                                }
                                TripPhase.RETURN, TripPhase.IDLE -> {
                                    // Calculate time between trips since last completed trip's endUnloadTime
                                    val lastEnd = lastCompletedTrip?.endUnloadTime ?: 0L
                                    if (lastEnd > 0) currentTimestamp - lastEnd else 0L
                                }
                            }
                        }

                        val timerLabel = if (tripPhase == TripPhase.IDLE || tripPhase == TripPhase.RETURN) {
                            L10n.get(L10nKey.TIME_BETWEEN_TRIPS, lang)
                        } else {
                            L10n.get(L10nKey.DURATION, lang)
                        }

                        Text(
                            text = timerLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        Text(
                            text = Constants.formatDurationExtended(activeDuration),
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (tripPhase == TripPhase.IDLE || tripPhase == TripPhase.RETURN) SkyBlue else MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }

            // 4. MAIN ACTION CONTROL PANEL (Glove friendly - Large interactive items)
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // PHASE 1: LOADING & WAITING
                        if (tripPhase == TripPhase.IDLE || tripPhase == TripPhase.RETURN || tripPhase == TripPhase.WAITING_BEFORE_LOADING) {
                            Text(
                                text = "STEP 1: LOADING AREA",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Waiting before load button
                                val isWaiting = tripPhase == TripPhase.WAITING_BEFORE_LOADING
                                Button(
                                    onClick = {
                                        if (isWaiting) {
                                            viewModel.stopWaitingBeforeLoading()
                                        } else {
                                            viewModel.startWaitingBeforeLoading()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isWaiting) SafetyRed else MaterialTheme.colorScheme.secondary,
                                        contentColor = IndustrialWhite
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(75.dp)
                                        .testTag("waiting_load_button")
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            imageVector = if (isWaiting) Icons.Default.Stop else Icons.Default.Pause,
                                            contentDescription = null
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = if (isWaiting) L10n.get(L10nKey.STOP_WAITING, lang) else L10n.get(L10nKey.START_WAITING, lang),
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center,
                                            fontSize = 12.sp
                                        )
                                    }
                                }

                                // Load button
                                Button(
                                    onClick = { showMachineDialog = true },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = SafetyGreen,
                                        contentColor = IndustrialWhite
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1.2f)
                                        .height(75.dp)
                                        .testTag("start_load_button")
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = L10n.get(L10nKey.START_LOADING, lang),
                                            fontWeight = FontWeight.ExtraBold,
                                            textAlign = TextAlign.Center,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }
                        }

                        // PHASE 2: ACTIVE LOADING
                        if (tripPhase == TripPhase.LOADING) {
                            Text(
                                text = "STEP 1: ACTIVE LOADING",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )

                            Button(
                                onClick = { viewModel.endLoading() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = SafetyGreen,
                                    contentColor = IndustrialWhite
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(75.dp)
                                    .testTag("end_load_button")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(28.dp))
                                    Text(
                                        text = L10n.get(L10nKey.END_LOADING, lang),
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 16.sp
                                    )
                                }
                            }
                        }

                        // PHASE 3: TRANSIT TO DUMP
                        if (tripPhase == TripPhase.TRANSIT) {
                            Text(
                                text = "STEP 2: TRANSIT TO UNLOAD",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )

                            Button(
                                onClick = { viewModel.arriveAtDumpPoint() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = SkyBlue,
                                    contentColor = IndustrialWhite
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(75.dp)
                                    .testTag("arrive_dump_button")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(28.dp))
                                    Text(
                                        text = L10n.get(L10nKey.ARRIVED_DUMP_POINT, lang),
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 16.sp
                                    )
                                }
                            }
                        }

                        // PHASE 4: WAITING AT UNLOAD & UNLOADING
                        if (tripPhase == TripPhase.WAITING_BEFORE_UNLOADING) {
                            Text(
                                text = "STEP 3: UNLOADING AREA",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )

                            Button(
                                onClick = { showDumpDialog = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = SafetyGreen,
                                    contentColor = IndustrialWhite
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(75.dp)
                                    .testTag("start_unload_button")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.LocalShipping, contentDescription = null, modifier = Modifier.size(28.dp))
                                    Text(
                                        text = L10n.get(L10nKey.START_UNLOADING, lang),
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 16.sp
                                    )
                                }
                            }
                        }

                        if (tripPhase == TripPhase.UNLOADING) {
                            Text(
                                text = "STEP 3: UNLOADING IN PROGRESS",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )

                            Button(
                                onClick = { viewModel.endUnloading() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = SafetyGreen,
                                    contentColor = IndustrialWhite
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(75.dp)
                                    .testTag("end_unload_button")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(28.dp))
                                    Text(
                                        text = L10n.get(L10nKey.END_UNLOADING, lang),
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 16.sp
                                    )
                                }
                            }
                        }

                        // 5. BREAKDOWN ACCORDION TRIGGER
                        Divider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

                        if (!isBreakdownActive) {
                            Button(
                                onClick = { showBreakdownDialog = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = SafetyRed,
                                    contentColor = IndustrialWhite
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("breakdown_trigger_button")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(20.dp))
                                    Text(
                                        text = L10n.get(L10nKey.START_BREAKDOWN, lang),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 6. MINI RECENT TRIPS LIST
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = L10n.get(L10nKey.TRIP_HISTORY, lang),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "${driversTrips.size} ${L10n.get(L10nKey.TOTAL_TRIPS, lang)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (driversTrips.isEmpty()) {
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
                items(driversTrips.take(4)) { trip ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "${L10n.get(L10nKey.LOADING_MACHINE, lang)}: ${trip.machine.ifEmpty { "N/A" }}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${L10n.get(L10nKey.DUMP_POINT, lang)}: ${trip.dumpPoint.ifEmpty { "N/A" }}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                                if (trip.breakdownType != null) {
                                    Text(
                                        text = "⚠️ ${trip.breakdownType}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SafetyRed,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Summarize durations
                            val loadDur = if (trip.endLoadTime != null && trip.startLoadTime != null) trip.endLoadTime - trip.startLoadTime else 0L
                            val unloadWaitDur = if (trip.startUnloadTime != null && trip.arrivalUnloadTime != null) trip.startUnloadTime - trip.arrivalUnloadTime else 0L
                            val unloadDur = if (trip.endUnloadTime != null && trip.startUnloadTime != null) trip.endUnloadTime - trip.startUnloadTime else 0L
                            
                            val totalTripDur = loadDur + unloadWaitDur + unloadDur

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = Constants.formatDuration(totalTripDur),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = trip.shift,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // --- DIALOGS FOR INPUTS ---

    // 1. SELECT MACHINE DIALOG
    if (showMachineDialog) {
        AlertDialog(
            onDismissRequest = { showMachineDialog = false },
            title = {
                Text(
                    text = L10n.get(L10nKey.SELECT_MACHINE, lang),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            },
            text = {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(Constants.MACHINES) { machine ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.startLoading(machine)
                                    showMachineDialog = false
                                }
                        ) {
                            Text(
                                text = machine,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showMachineDialog = false }) {
                    Text(L10n.get(L10nKey.CANCEL, lang), color = MaterialTheme.colorScheme.primary)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // 2. SELECT DUMP POINT DIALOG
    if (showDumpDialog) {
        AlertDialog(
            onDismissRequest = { showDumpDialog = false },
            title = {
                Text(
                    text = L10n.get(L10nKey.SELECT_DUMP_POINT, lang),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            },
            text = {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(Constants.DUMP_POINTS) { dump ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.startUnloading(dump)
                                    showDumpDialog = false
                                }
                        ) {
                            Text(
                                text = dump,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showDumpDialog = false }) {
                    Text(L10n.get(L10nKey.CANCEL, lang), color = MaterialTheme.colorScheme.primary)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // 3. START BREAKDOWN DIALOG
    if (showBreakdownDialog) {
        AlertDialog(
            onDismissRequest = { showBreakdownDialog = false },
            title = {
                Text(
                    text = L10n.get(L10nKey.BREAKDOWN_TYPE, lang),
                    fontWeight = FontWeight.Bold,
                    color = SafetyRed
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Constants.BREAKDOWN_TYPES.forEach { type ->
                        val localizedType = when (type) {
                            "Fuel" -> L10n.get(L10nKey.FUEL, lang)
                            "Hydraulic oil" -> L10n.get(L10nKey.HYDRAULIC_OIL, lang)
                            "Mechanical issue" -> L10n.get(L10nKey.MECHANICAL_ISSUE, lang)
                            else -> L10n.get(L10nKey.OTHER, lang)
                        }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.startBreakdown(localizedType)
                                    showBreakdownDialog = false
                                }
                        ) {
                            Text(
                                text = localizedType,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showBreakdownDialog = false }) {
                    Text(L10n.get(L10nKey.CANCEL, lang), color = MaterialTheme.colorScheme.primary)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}
