package com.paxel.arspacescan.util

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import com.paxel.arspacescan.R
import com.paxel.arspacescan.data.model.PackageMeasurement
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CsvExporter {

    fun exportMeasurements(context: Context, measurements: List<PackageMeasurement>) {
        if (measurements.isEmpty()) {
            Toast.makeText(context, context.getString(R.string.no_data_to_export), Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val csvContent = generateCsvContent(measurements)
            val filename = "paxel_measurements_${System.currentTimeMillis()}.csv"

            saveCsvFile(context, csvContent, filename)
        } catch (e: Exception) {
            Toast.makeText(context, context.getString(R.string.export_failed), Toast.LENGTH_SHORT).show()
        }
    }

    private fun generateCsvContent(measurements: List<PackageMeasurement>): String {
        val csvHeader = "ID,Package Name,Declared Size,Width(cm),Height(cm),Depth(cm),Volume(cm³),Timestamp,Has Photo,Validated\n"
        val csvContent = StringBuilder(csvHeader)

        measurements.forEach { measurement ->
            csvContent.append("${measurement.id},")
            csvContent.append("\"${measurement.packageName}\",")
            csvContent.append("\"${measurement.declaredSize}\",")
            csvContent.append("${String.format("%.2f", measurement.width * 100)},")
            csvContent.append("${String.format("%.2f", measurement.height * 100)},")
            csvContent.append("${String.format("%.2f", measurement.depth * 100)},")
            csvContent.append("${String.format("%.2f", measurement.volume * 1_000_000)},")
            csvContent.append("${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(measurement.timestamp))},")
            csvContent.append("${if (measurement.imagePath != null) "Yes" else "No"},")
            csvContent.append("${measurement.isValidated}\n")
        }

        return csvContent.toString()
    }

    private fun saveCsvFile(context: Context, content: String, filename: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Modern approach for Android 10+
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }

            context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)?.let { uri ->
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(content.toByteArray())
                    Toast.makeText(context, context.getString(R.string.export_success), Toast.LENGTH_LONG).show()
                }
            } ?: run {
                Toast.makeText(context, context.getString(R.string.export_failed), Toast.LENGTH_SHORT).show()
            }
        } else {
            // Legacy approach for older Android versions
            @Suppress("DEPRECATION")
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadsDir, filename)

            FileOutputStream(file).use { it.write(content.toByteArray()) }
            Toast.makeText(context, context.getString(R.string.export_success), Toast.LENGTH_LONG).show()
        }
    }
}
