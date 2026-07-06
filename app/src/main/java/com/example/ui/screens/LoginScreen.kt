package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Constants
import com.example.ui.localization.AppLanguage
import com.example.ui.localization.L10n
import com.example.ui.localization.L10nKey
import com.example.ui.viewmodel.TruckCycleViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: TruckCycleViewModel,
    modifier: Modifier = Modifier
) {
    val lang by viewModel.appLanguage.collectAsState()
    
    var tempDriver by remember { mutableStateOf("") }
    var tempTruck by remember { mutableStateOf(Constants.TRUCKS.first()) }
    var tempShift by remember { mutableStateOf("Morning") } // "Morning", "Afternoon", "Night"

    var truckDropdownExpanded by remember { mutableStateOf(false) }
    var shiftDropdownExpanded by remember { mutableStateOf(false) }
    var langDropdownExpanded by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    // Map shift strings to localized names
    val shiftDisplayMap = mapOf(
        "Morning" to L10n.get(L10nKey.MORNING, lang),
        "Afternoon" to L10n.get(L10nKey.AFTERNOON, lang),
        "Night" to L10n.get(L10nKey.NIGHT, lang)
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Language Switcher Button at top right
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentSize(Alignment.TopEnd)
            ) {
                Button(
                    onClick = { langDropdownExpanded = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "🌐 ${lang.displayName}",
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                DropdownMenu(
                    expanded = langDropdownExpanded,
                    onDismissRequest = { langDropdownExpanded = false },
                    modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                ) {
                    AppLanguage.values().forEach { appLang ->
                        DropdownMenuItem(
                            text = { 
                                Text(
                                    text = appLang.displayName,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (appLang == lang) FontWeight.Bold else FontWeight.Normal
                                ) 
                            },
                            onClick = {
                                viewModel.setLanguage(appLang)
                                langDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            // High-Vis Industrial Logo Icon
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.LocalShipping,
                    contentDescription = "Truck Cycle Logo",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(54.dp)
                )
            }

            // Title and Slogan
            Text(
                text = L10n.get(L10nKey.APP_NAME, lang),
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp,
                    textAlign = TextAlign.Center
                )
            )

            Text(
                text = "Phosphate Fleet Tracking System\nنظام تتبع أسطول الفوسفات",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Form container
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Driver Name field
                    Text(
                        text = L10n.get(L10nKey.DRIVER_NAME, lang),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    OutlinedTextField(
                        value = tempDriver,
                        onValueChange = { tempDriver = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("driver_name_input"),
                        leadingIcon = {
                            Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        placeholder = {
                            Text(
                                text = L10n.get(L10nKey.SELECT_DRIVER, lang),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            cursorColor = MaterialTheme.colorScheme.primary
                        ),
                        singleLine = true
                    )

                    // Truck selection dropdown
                    Text(
                        text = L10n.get(L10nKey.TRUCK_MATRICULE, lang),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = tempTruck,
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("truck_select_input")
                                .clickable { truckDropdownExpanded = true },
                            enabled = false, // disable key input, rely on clicking box/dropdown
                            leadingIcon = {
                                Icon(Icons.Default.LocalShipping, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable { truckDropdownExpanded = true }
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                disabledBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                                disabledLeadingIconColor = MaterialTheme.colorScheme.primary,
                                disabledTrailingIconColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        // Invisible click handler to cover the entire field
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable { truckDropdownExpanded = true }
                        )

                        DropdownMenu(
                            expanded = truckDropdownExpanded,
                            onDismissRequest = { truckDropdownExpanded = false },
                            modifier = Modifier
                                .fillMaxWidth(0.8f)
                                .heightIn(max = 250.dp)
                                .background(MaterialTheme.colorScheme.surface)
                        ) {
                            Constants.TRUCKS.forEach { truck ->
                                DropdownMenuItem(
                                    text = { Text(truck, color = MaterialTheme.colorScheme.onSurface) },
                                    onClick = {
                                        tempTruck = truck
                                        truckDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Shift selection dropdown
                    Text(
                        text = L10n.get(L10nKey.SHIFT, lang),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = shiftDisplayMap[tempShift] ?: tempShift,
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("shift_select_input")
                                .clickable { shiftDropdownExpanded = true },
                            enabled = false,
                            leadingIcon = {
                                Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable { shiftDropdownExpanded = true }
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                disabledBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                                disabledLeadingIconColor = MaterialTheme.colorScheme.primary,
                                disabledTrailingIconColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        // Invisible click handler to cover the entire field
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable { shiftDropdownExpanded = true }
                        )

                        DropdownMenu(
                            expanded = shiftDropdownExpanded,
                            onDismissRequest = { shiftDropdownExpanded = false },
                            modifier = Modifier
                                .fillMaxWidth(0.8f)
                                .background(MaterialTheme.colorScheme.surface)
                        ) {
                            listOf("Morning", "Afternoon", "Night").forEach { shiftVal ->
                                DropdownMenuItem(
                                    text = { Text(shiftDisplayMap[shiftVal] ?: shiftVal, color = MaterialTheme.colorScheme.onSurface) },
                                    onClick = {
                                        tempShift = shiftVal
                                        shiftDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Giant Login Button
            Button(
                onClick = {
                    if (tempDriver.trim().isNotEmpty()) {
                        viewModel.login(tempDriver.trim(), tempTruck, tempShift)
                    }
                },
                enabled = tempDriver.trim().isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                    disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("login_button")
            ) {
                Text(
                    text = L10n.get(L10nKey.LOGIN, lang),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}
