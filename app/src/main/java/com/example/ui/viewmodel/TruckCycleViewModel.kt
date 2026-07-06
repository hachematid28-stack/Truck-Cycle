package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.database.TripDatabase
import com.example.data.database.TripEntity
import com.example.data.model.Constants
import com.example.data.repository.TripRepository
import com.example.ui.localization.AppLanguage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class TripPhase {
    IDLE,
    WAITING_BEFORE_LOADING,
    LOADING,
    TRANSIT,
    WAITING_BEFORE_UNLOADING,
    UNLOADING,
    RETURN
}

class TruckCycleViewModel(
    application: Application,
    private val repository: TripRepository
) : AndroidViewModel(application) {

    private val sharedPrefs = application.getSharedPreferences("truck_cycle_prefs", Context.MODE_PRIVATE)

    // User Session State
    val isLoggedIn = MutableStateFlow(sharedPrefs.getBoolean("is_logged_in", false))
    val driverName = MutableStateFlow(sharedPrefs.getString("driver_name", "") ?: "")
    val truckMatricule = MutableStateFlow(sharedPrefs.getString("truck_matricule", "") ?: "")
    val activeShift = MutableStateFlow(sharedPrefs.getString("active_shift", "") ?: "")
    val appLanguage = MutableStateFlow(AppLanguage.valueOf(sharedPrefs.getString("app_language", AppLanguage.AR.name) ?: AppLanguage.AR.name))

    // Active Trip State loaded from DB or Memory
    val activeTrip = MutableStateFlow<TripEntity?>(null)
    val tripPhase = MutableStateFlow(TripPhase.IDLE)
    val isBreakdownActive = MutableStateFlow(false)

    // Last completed trip for time between trips calculation
    val lastCompletedTrip = MutableStateFlow<TripEntity?>(null)

    // Live Ticker for active durations (updates every 500ms)
    val currentTimestamp = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(500)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), System.currentTimeMillis())

    // All trips history
    val allTrips: StateFlow<List<TripEntity>> = repository.allTrips
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            // Read active trip from database on startup
            val dbActive = repository.getActiveTrip()
            if (dbActive != null) {
                activeTrip.value = dbActive
                determinePhase(dbActive)
                if (dbActive.breakdownStart != null && dbActive.breakdownEnd == null) {
                    isBreakdownActive.value = true
                }
            }
            
            // Read last completed trip for truck
            if (truckMatricule.value.isNotEmpty()) {
                lastCompletedTrip.value = repository.getLastCompletedTripForTruck(truckMatricule.value)
            }
        }
    }

    private fun determinePhase(trip: TripEntity) {
        if (trip.endUnloadTime != null) {
            tripPhase.value = TripPhase.RETURN
        } else if (trip.startUnloadTime != null) {
            tripPhase.value = TripPhase.UNLOADING
        } else if (trip.arrivalUnloadTime != null) {
            tripPhase.value = TripPhase.WAITING_BEFORE_UNLOADING
        } else if (trip.endLoadTime != null) {
            tripPhase.value = TripPhase.TRANSIT
        } else if (trip.startLoadTime != null) {
            tripPhase.value = TripPhase.LOADING
        } else if (trip.waitingStart != null) {
            tripPhase.value = TripPhase.WAITING_BEFORE_LOADING
        } else {
            tripPhase.value = TripPhase.IDLE
        }
    }

    fun login(driver: String, truck: String, shift: String) {
        driverName.value = driver
        truckMatricule.value = truck
        activeShift.value = shift
        isLoggedIn.value = true

        sharedPrefs.edit().apply {
            putString("driver_name", driver)
            putString("truck_matricule", truck)
            putString("active_shift", shift)
            putBoolean("is_logged_in", true)
            apply()
        }

        // Fetch last completed trip for truck
        viewModelScope.launch {
            lastCompletedTrip.value = repository.getLastCompletedTripForTruck(truck)
            val dbActive = repository.getActiveTrip()
            if (dbActive != null) {
                activeTrip.value = dbActive
                determinePhase(dbActive)
            } else {
                activeTrip.value = null
                tripPhase.value = TripPhase.IDLE
            }
        }
    }

    fun logout() {
        driverName.value = ""
        truckMatricule.value = ""
        activeShift.value = ""
        isLoggedIn.value = false
        activeTrip.value = null
        tripPhase.value = TripPhase.IDLE
        isBreakdownActive.value = false

        sharedPrefs.edit().apply {
            putString("driver_name", "")
            putString("truck_matricule", "")
            putString("active_shift", "")
            putBoolean("is_logged_in", false)
            apply()
        }
    }

    fun setLanguage(language: AppLanguage) {
        appLanguage.value = language
        sharedPrefs.edit().putString("app_language", language.name).apply()
    }

    // --- TIME TRACKING ACTIONS ---

    // 1. WAITING TIME BEFORE LOADING
    fun startWaitingBeforeLoading() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val newTrip = TripEntity(
                driverName = driverName.value,
                truck = truckMatricule.value,
                machine = "",
                dumpPoint = "",
                shift = activeShift.value,
                waitingStart = now
            )
            val id = repository.insertTrip(newTrip)
            val insertedTrip = newTrip.copy(id = id.toInt())
            activeTrip.value = insertedTrip
            tripPhase.value = TripPhase.WAITING_BEFORE_LOADING
        }
    }

    fun stopWaitingBeforeLoading() {
        val current = activeTrip.value ?: return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val updated = current.copy(
                waitingEnd = now
            )
            repository.updateTrip(updated)
            activeTrip.value = updated
            tripPhase.value = TripPhase.IDLE
        }
    }

    // 2. LOAD CYCLE
    fun startLoading(machineName: String) {
        val current = activeTrip.value
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            if (current != null) {
                // We were already waiting, transition to loading
                val updated = current.copy(
                    machine = machineName,
                    startLoadTime = now,
                    // If we hadn't ended waiting yet, end it now automatically
                    waitingEnd = current.waitingEnd ?: now
                )
                repository.updateTrip(updated)
                activeTrip.value = updated
            } else {
                // Starting fresh loading without waiting
                val newTrip = TripEntity(
                    driverName = driverName.value,
                    truck = truckMatricule.value,
                    machine = machineName,
                    dumpPoint = "",
                    shift = activeShift.value,
                    startLoadTime = now
                )
                val id = repository.insertTrip(newTrip)
                activeTrip.value = newTrip.copy(id = id.toInt())
            }
            tripPhase.value = TripPhase.LOADING
        }
    }

    fun endLoading() {
        val current = activeTrip.value ?: return
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            val updated = current.copy(
                endLoadTime = now
            )
            repository.updateTrip(updated)
            activeTrip.value = updated
            tripPhase.value = TripPhase.TRANSIT
        }
    }

    // 3. TRANSIT & ARRIVAL AT DUMP POINT
    fun arriveAtDumpPoint() {
        val current = activeTrip.value ?: return
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            val updated = current.copy(
                arrivalUnloadTime = now
            )
            repository.updateTrip(updated)
            activeTrip.value = updated
            tripPhase.value = TripPhase.WAITING_BEFORE_UNLOADING
        }
    }

    // 4. UNLOAD CYCLE
    fun startUnloading(dumpPointName: String) {
        val current = activeTrip.value ?: return
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            val updated = current.copy(
                dumpPoint = dumpPointName,
                startUnloadTime = now,
                // End loading transit if needed
                arrivalUnloadTime = current.arrivalUnloadTime ?: now
            )
            repository.updateTrip(updated)
            activeTrip.value = updated
            tripPhase.value = TripPhase.UNLOADING
        }
    }

    fun endUnloading() {
        val current = activeTrip.value ?: return
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            val updated = current.copy(
                endUnloadTime = now,
                isCompleted = true
            )
            repository.updateTrip(updated)
            
            // Store as last completed trip for return transit
            lastCompletedTrip.value = updated
            
            // Clear active trip
            activeTrip.value = null
            tripPhase.value = TripPhase.IDLE
        }
    }

    // 5. BREAKDOWN MODULE
    fun startBreakdown(type: String) {
        val current = activeTrip.value
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            isBreakdownActive.value = true
            if (current != null) {
                val updated = current.copy(
                    breakdownType = type,
                    breakdownStart = now,
                    breakdownEnd = null
                )
                repository.updateTrip(updated)
                activeTrip.value = updated
            } else {
                // Create placeholder trip for the breakdown
                val placeholderTrip = TripEntity(
                    driverName = driverName.value,
                    truck = truckMatricule.value,
                    machine = "N/A",
                    dumpPoint = "N/A",
                    shift = activeShift.value,
                    breakdownType = type,
                    breakdownStart = now,
                    breakdownEnd = null
                )
                val id = repository.insertTrip(placeholderTrip)
                activeTrip.value = placeholderTrip.copy(id = id.toInt())
            }
        }
    }

    fun endBreakdown() {
        val current = activeTrip.value ?: return
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            isBreakdownActive.value = false
            val updated = current.copy(
                breakdownEnd = now
            )
            repository.updateTrip(updated)
            
            // If this was a breakdown-only placeholder trip, complete it fully
            if (updated.machine == "N/A" && updated.dumpPoint == "N/A") {
                val fullyCompletedPlaceholder = updated.copy(
                    endUnloadTime = now,
                    isCompleted = true
                )
                repository.updateTrip(fullyCompletedPlaceholder)
                activeTrip.value = null
                tripPhase.value = TripPhase.IDLE
            } else {
                activeTrip.value = updated
                determinePhase(updated)
            }
        }
    }

    fun deleteTrip(trip: TripEntity) {
        viewModelScope.launch {
            repository.deleteTrip(trip)
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            repository.clearAllTrips()
            activeTrip.value = null
            lastCompletedTrip.value = null
            tripPhase.value = TripPhase.IDLE
            isBreakdownActive.value = false
        }
    }

    companion object {
        fun provideFactory(
            application: Application,
            repository: TripRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return TruckCycleViewModel(application, repository) as T
            }
        }
    }
}
