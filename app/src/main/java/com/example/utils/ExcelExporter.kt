package com.example.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.database.TripEntity
import com.example.data.model.Constants
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ExcelExporter {

    fun exportAndShareExcel(
        context: Context,
        trips: List<TripEntity>,
        selectedDriver: String,
        selectedTruck: String,
        selectedShift: String
    ) {
        try {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            val dateStr = sdf.format(Date())

            val cacheDir = context.externalCacheDir ?: context.cacheDir
            val reportsDir = File(cacheDir, "reports").apply { mkdirs() }
            val file = File(reportsDir, "phosphate_fleet_report_${System.currentTimeMillis()}.csv")

            val writer = FileWriter(file)

            // 1. Write Header Info
            writer.append("PHOSPHATE FLEET REPORT (EXCEL COMPATIBLE CSV)\n")
            writer.append("Generated Date,${dateStr}\n")
            writer.append("Filtered Driver,${selectedDriver.ifEmpty { "All" }}\n")
            writer.append("Filtered Truck,${selectedTruck.ifEmpty { "All" }}\n")
            writer.append("Filtered Shift,${selectedShift.ifEmpty { "All" }}\n")
            writer.append("\n")

            // 2. Metrics Summaries
            val totalTrips = trips.size
            var totalWaiting: Long = 0
            var totalBreakdown: Long = 0
            var earliestStart: Long = Long.MAX_VALUE
            var latestEnd: Long = Long.MIN_VALUE

            for (t in trips) {
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
                totalTrips * 30 * 60 * 1000L
            }

            val totalWorking = maxOf(0L, totalSpan - totalBreakdown)
            val productivity = if (totalWorking > 0) {
                val productiveTime = maxOf(0L, totalWorking - totalWaiting)
                ((productiveTime.toFloat() / totalWorking.toFloat()) * 100).toInt().coerceIn(0, 100)
            } else {
                100
            }

            writer.append("METRICS SUMMARY\n")
            writer.append("Total Trips,${totalTrips}\n")
            writer.append("Total Working Time,${Constants.formatDurationExtended(totalWorking)}\n")
            writer.append("Total Waiting Time,${Constants.formatDurationExtended(totalWaiting)}\n")
            writer.append("Total Breakdown Time,${Constants.formatDurationExtended(totalBreakdown)}\n")
            writer.append("Productivity Percentage,${productivity}%\n")
            writer.append("\n")

            // 3. Table Column Headers
            val itemSdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            writer.append("Trip ID,Driver,Truck,Shift,Machine,Dump Point,Load Start,Load End,Waiting Start,Waiting End,Breakdown Type,Breakdown Start,BreakdownEnd,Arrival Unload,Start Unload,End Unload,Load Duration,Waiting Duration,Unload Wait Duration,Breakdown Duration\n")

            // 4. Table Rows
            for ((index, t) in trips.withIndex()) {
                val tripId = index + 1
                val driver = t.driverName.replace(",", " ")
                val truck = t.truck
                val shift = t.shift
                val machine = t.machine
                val dumpPoint = t.dumpPoint
                
                val startLoad = t.startLoadTime?.let { itemSdf.format(Date(it)) } ?: ""
                val endLoad = t.endLoadTime?.let { itemSdf.format(Date(it)) } ?: ""
                val waitStart = t.waitingStart?.let { itemSdf.format(Date(it)) } ?: ""
                val waitEnd = t.waitingEnd?.let { itemSdf.format(Date(it)) } ?: ""
                val bType = t.breakdownType ?: ""
                val bStart = t.breakdownStart?.let { itemSdf.format(Date(it)) } ?: ""
                val bEnd = t.breakdownEnd?.let { itemSdf.format(Date(it)) } ?: ""
                
                val arrUnload = t.arrivalUnloadTime?.let { itemSdf.format(Date(it)) } ?: ""
                val stUnload = t.startUnloadTime?.let { itemSdf.format(Date(it)) } ?: ""
                val ndUnload = t.endUnloadTime?.let { itemSdf.format(Date(it)) } ?: ""

                val loadDur = if (t.endLoadTime != null && t.startLoadTime != null) Constants.formatDuration(t.endLoadTime - t.startLoadTime) else ""
                val waitDur = if (t.waitingEnd != null && t.waitingStart != null) Constants.formatDuration(t.waitingEnd - t.waitingStart) else ""
                val unlWaitDur = if (t.startUnloadTime != null && t.arrivalUnloadTime != null) Constants.formatDuration(t.startUnloadTime - t.arrivalUnloadTime) else ""
                val bdDur = if (t.breakdownEnd != null && t.breakdownStart != null) Constants.formatDuration(t.breakdownEnd - t.breakdownStart) else ""

                writer.append("$tripId,$driver,$truck,$shift,$machine,$dumpPoint,$startLoad,$endLoad,$waitStart,$waitEnd,$bType,$bStart,$bEnd,$arrUnload,$stUnload,$ndUnload,$loadDur,$waitDur,$unlWaitDur,$bdDur\n")
            }

            writer.flush()
            writer.close()

            // Open share menu
            val fileUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, fileUri)
                putExtra(Intent.EXTRA_SUBJECT, "Phosphate Fleet Report Excel - " + selectedDriver)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share Report Excel (CSV)"))

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error exporting CSV Excel: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
