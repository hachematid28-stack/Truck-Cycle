package com.example.utils

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.database.TripEntity
import com.example.data.model.Constants
import com.example.ui.localization.AppLanguage
import com.example.ui.localization.L10n
import com.example.ui.localization.L10nKey
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfExporter {

    fun exportAndSharePdf(
        context: Context,
        trips: List<TripEntity>,
        language: AppLanguage,
        selectedDriver: String,
        selectedTruck: String,
        selectedShift: String
    ) {
        try {
            val pdfDocument = PdfDocument()
            
            // Standard A4 dimensions: 595 x 842 points (72 points per inch)
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas: Canvas = page.canvas

            val paint = Paint()
            val textPaint = Paint().apply {
                isAntiAlias = true
                textSize = 12f
                color = Color.BLACK
            }
            
            val headerPaint = Paint().apply {
                isAntiAlias = true
                textSize = 20f
                isFakeBoldText = true
                color = Color.rgb(18, 30, 49) // Slate Blue/Black
            }

            val accentPaint = Paint().apply {
                isAntiAlias = true
                textSize = 11f
                color = Color.rgb(120, 120, 120)
            }

            val tableHeaderPaint = Paint().apply {
                isAntiAlias = true
                textSize = 10f
                isFakeBoldText = true
                color = Color.WHITE
            }

            val borderPaint = Paint().apply {
                color = Color.LTGRAY
                style = Paint.Style.STROKE
                strokeWidth = 1f
            }

            val fillPaint = Paint().apply {
                color = Color.rgb(240, 240, 245)
                style = Paint.Style.FILL
            }

            val headerFillPaint = Paint().apply {
                color = Color.rgb(27, 38, 59)
                style = Paint.Style.FILL
            }

            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            val dateStr = sdf.format(Date())

            // 1. Title Block
            canvas.drawText("PHOSPHATE FLEET REPORT", 40f, 50f, headerPaint)
            canvas.drawText("Generated: $dateStr", 40f, 70f, accentPaint)
            canvas.drawLine(40f, 80f, 555f, 80f, borderPaint)

            // 2. Metadata / Summary Information
            paint.color = Color.rgb(248, 249, 250)
            paint.style = Paint.Style.FILL
            canvas.drawRect(40f, 90f, 555f, 160f, paint)
            canvas.drawRect(40f, 90f, 555f, 160f, borderPaint)

            textPaint.isFakeBoldText = true
            canvas.drawText("Driver: ${selectedDriver.ifEmpty { "All" }}", 50f, 110f, textPaint)
            canvas.drawText("Truck: ${selectedTruck.ifEmpty { "All" }}", 50f, 130f, textPaint)
            canvas.drawText("Shift: ${selectedShift.ifEmpty { "All" }}", 50f, 150f, textPaint)

            val totalTrips = trips.size
            var totalWaiting: Long = 0
            var totalBreakdown: Long = 0
            var totalLoadTime: Long = 0
            var earliestStart: Long = Long.MAX_VALUE
            var latestEnd: Long = Long.MIN_VALUE

            for (t in trips) {
                // waiting
                val w = (t.waitingEnd ?: 0L) - (t.waitingStart ?: 0L)
                val wu = (t.startUnloadTime ?: 0L) - (t.arrivalUnloadTime ?: 0L)
                totalWaiting += if (w > 0) w else 0
                totalWaiting += if (wu > 0) wu else 0

                // breakdown
                val b = (t.breakdownEnd ?: 0L) - (t.breakdownStart ?: 0L)
                totalBreakdown += if (b > 0) b else 0

                // loading
                val l = (t.endLoadTime ?: 0L) - (t.startLoadTime ?: 0L)
                totalLoadTime += if (l > 0) l else 0

                // dates
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
                totalTrips * 30 * 60 * 1000L // 30 mins per trip fallback
            }

            val totalWorking = maxOf(0L, totalSpan - totalBreakdown)
            val productivity = if (totalWorking > 0) {
                val productiveTime = maxOf(0L, totalWorking - totalWaiting)
                ((productiveTime.toFloat() / totalWorking.toFloat()) * 100).toInt().coerceIn(0, 100)
            } else {
                100
            }

            canvas.drawText("Total Trips: $totalTrips", 300f, 110f, textPaint)
            canvas.drawText("Total Waiting: ${Constants.formatDurationExtended(totalWaiting)}", 300f, 130f, textPaint)
            canvas.drawText("Total Breakdown: ${Constants.formatDurationExtended(totalBreakdown)}", 300f, 150f, textPaint)
            textPaint.isFakeBoldText = false

            // 3. Table Headers
            val tableTop = 180f
            val colWidths = floatArrayOf(40f, 80f, 80f, 80f, 110f, 125f) // Total: 515. 40 + colWidths sum = 555
            val headers = arrayOf("ID", "Driver", "Truck", "Shift", "Machine/Dump", "Durations")

            canvas.drawRect(40f, tableTop, 555f, tableTop + 25f, headerFillPaint)
            canvas.drawRect(40f, tableTop, 555f, tableTop + 25f, borderPaint)

            var currentX = 40f
            for (i in headers.indices) {
                val headerText = headers[i]
                canvas.drawText(headerText, currentX + 5f, tableTop + 17f, tableHeaderPaint)
                currentX += colWidths[i]
            }

            // 4. Table Body
            var currentY = tableTop + 25f
            val itemSdf = SimpleDateFormat("HH:mm", Locale.getDefault())

            val limitTrips = trips.take(15) // Limit to fit on 1 page gracefully
            for ((index, trip) in limitTrips.withIndex()) {
                if (currentY + 30f > 800f) break // Avoid page overflow for safety
                
                // Zebra stripe background
                if (index % 2 == 1) {
                    paint.color = Color.rgb(245, 245, 250)
                    paint.style = Paint.Style.FILL
                    canvas.drawRect(40f, currentY, 555f, currentY + 30f, paint)
                }
                canvas.drawRect(40f, currentY, 555f, currentY + 30f, borderPaint)

                var x = 40f
                val tripId = (index + 1).toString()
                val driver = trip.driverName.take(10)
                val truck = trip.truck
                val shift = trip.shift
                val location = "M: ${trip.machine}\nDP: ${trip.dumpPoint}"

                // Calculate cycle times
                val loadDur = if (trip.endLoadTime != null && trip.startLoadTime != null) trip.endLoadTime - trip.startLoadTime else 0L
                val waitDur = if (trip.waitingEnd != null && trip.waitingStart != null) trip.waitingEnd - trip.waitingStart else 0L
                val unloadWaitDur = if (trip.startUnloadTime != null && trip.arrivalUnloadTime != null) trip.startUnloadTime - trip.arrivalUnloadTime else 0L
                
                val breakdownDur = if (trip.breakdownEnd != null && trip.breakdownStart != null) trip.breakdownEnd - trip.breakdownStart else 0L

                val durationText = "Ld:${Constants.formatDuration(loadDur)} Wt:${Constants.formatDuration(waitDur + unloadWaitDur)}" + 
                    if (breakdownDur > 0) " Bd:${Constants.formatDuration(breakdownDur)}" else ""

                // Draw columns
                val smallTextPaint = Paint().apply {
                    isAntiAlias = true
                    textSize = 8.5f
                    color = Color.BLACK
                }

                canvas.drawText(tripId, x + 5f, currentY + 18f, smallTextPaint)
                x += colWidths[0]
                canvas.drawText(driver, x + 5f, currentY + 18f, smallTextPaint)
                x += colWidths[1]
                canvas.drawText(truck, x + 5f, currentY + 18f, smallTextPaint)
                x += colWidths[2]
                canvas.drawText(shift, x + 5f, currentY + 18f, smallTextPaint)
                x += colWidths[3]
                
                // Machine / Dump (Multiline)
                canvas.drawText("M:${trip.machine}", x + 5f, currentY + 12f, smallTextPaint)
                canvas.drawText("D:${trip.dumpPoint}", x + 5f, currentY + 22f, smallTextPaint)
                x += colWidths[4]

                canvas.drawText(durationText, x + 5f, currentY + 18f, smallTextPaint)

                currentY += 30f
            }

            // If there were more trips than listed, show indicators
            if (trips.size > 15) {
                canvas.drawText("... and ${trips.size - 15} more records ...", 40f, currentY + 15f, accentPaint)
                currentY += 20f
            }

            // Summary section footer
            val footerY = 780f
            canvas.drawLine(40f, footerY, 555f, footerY, borderPaint)
            val footerText = "Truck Cycle App | Productivity Index: $productivity% | Offline Verified SQLite Data"
            canvas.drawText(footerText, 40f, footerY + 20f, accentPaint)

            pdfDocument.finishPage(page)

            // Save the document to external cache so it can be shared with FileProvider
            val cacheDir = context.externalCacheDir ?: context.cacheDir
            val reportsDir = File(cacheDir, "reports").apply { mkdirs() }
            val file = File(reportsDir, "phosphate_fleet_report_${System.currentTimeMillis()}.pdf")
            
            val fileOutputStream = FileOutputStream(file)
            pdfDocument.writeTo(fileOutputStream)
            pdfDocument.close()
            fileOutputStream.close()

            // Open share menu
            val fileUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, fileUri)
                putExtra(Intent.EXTRA_SUBJECT, "Phosphate Fleet Report - " + selectedDriver)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share Report PDF"))

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error exporting PDF: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
