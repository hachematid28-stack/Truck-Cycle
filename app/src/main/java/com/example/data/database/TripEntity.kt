package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val driverName: String,
    val truck: String,
    val machine: String,
    val dumpPoint: String,
    val shift: String,
    val startLoadTime: Long? = null,
    val endLoadTime: Long? = null,
    val waitingStart: Long? = null,
    val waitingEnd: Long? = null,
    val breakdownType: String? = null,
    val breakdownStart: Long? = null,
    val breakdownEnd: Long? = null,
    
    // Additional fields to calculate "waiting before unloading" and "time between trips"
    val arrivalUnloadTime: Long? = null,
    val startUnloadTime: Long? = null,
    val endUnloadTime: Long? = null,
    
    val isCompleted: Boolean = false
)
