package com.example.data.repository

import com.example.data.database.TripDao
import com.example.data.database.TripEntity
import kotlinx.coroutines.flow.Flow

class TripRepository(private val tripDao: TripDao) {
    val allTrips: Flow<List<TripEntity>> = tripDao.getAllTrips()

    suspend fun getActiveTrip(): TripEntity? {
        return tripDao.getActiveTrip()
    }

    suspend fun getLastCompletedTripForTruck(truck: String): TripEntity? {
        return tripDao.getLastCompletedTripForTruck(truck)
    }

    suspend fun insertTrip(trip: TripEntity): Long {
        return tripDao.insertTrip(trip)
    }

    suspend fun updateTrip(trip: TripEntity) {
        tripDao.updateTrip(trip)
    }

    suspend fun deleteTrip(trip: TripEntity) {
        tripDao.deleteTrip(trip)
    }

    suspend fun clearAllTrips() {
        tripDao.clearAllTrips()
    }
}
