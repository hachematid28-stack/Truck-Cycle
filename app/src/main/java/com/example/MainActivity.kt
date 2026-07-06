package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.data.database.TripDatabase
import com.example.data.repository.TripRepository
import com.example.ui.localization.L10n
import com.example.ui.localization.L10nKey
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.ReportsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.TruckCycleViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize database and repository
        val database = TripDatabase.getDatabase(applicationContext)
        val repository = TripRepository(database.tripDao())
        
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val viewModel: TruckCycleViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                    factory = TruckCycleViewModel.provideFactory(application, repository)
                )

                val isLoggedIn by viewModel.isLoggedIn.collectAsState()
                val lang by viewModel.appLanguage.collectAsState()

                if (!isLoggedIn) {
                    LoginScreen(viewModel = viewModel, modifier = Modifier.fillMaxSize())
                } else {
                    var currentTab by remember { mutableStateOf("dashboard") } // "dashboard" or "reports"

                    Scaffold(
                        bottomBar = {
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ) {
                                NavigationBarItem(
                                    selected = currentTab == "dashboard",
                                    onClick = { currentTab = "dashboard" },
                                    icon = { Icon(Icons.Default.Dashboard, contentDescription = null) },
                                    label = { Text(L10n.get(L10nKey.LOAD_CYCLE, lang)) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primary,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    ),
                                    modifier = Modifier.testTag("dashboard_nav_tab")
                                )
                                NavigationBarItem(
                                    selected = currentTab == "reports",
                                    onClick = { currentTab = "reports" },
                                    icon = { Icon(Icons.Default.Analytics, contentDescription = null) },
                                    label = { Text(L10n.get(L10nKey.REPORTS, lang)) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primary,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    ),
                                    modifier = Modifier.testTag("reports_nav_tab")
                                )
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    ) { innerPadding ->
                        val contentModifier = Modifier.padding(innerPadding)
                        
                        if (currentTab == "dashboard") {
                            DashboardScreen(viewModel = viewModel, modifier = contentModifier)
                        } else {
                            ReportsScreen(viewModel = viewModel, modifier = contentModifier)
                        }
                    }
                }
            }
        }
    }
}
