package com.paxel.arspacescan.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import com.paxel.arspacescan.data.model.PackageMeasurement
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Object singleton untuk menangani ekspor data pengukuran ke file CSV.
 */
object CsvExporter {

    private const val TAG = "CsvExporter"
    private const val CSV_HEADER = "Timestamp,Nama Paket,Ukuran Dideklarasikan,Panjang (cm),Lebar (cm),Tinggi (cm),Volume (cm³),Kategori Ukuran,Estimasi Harga (Rp),Path Gambar,Sudah Divalidasi"

    /**
     * Mengubah daftar data pengukuran menjadi format string CSV.
     */
    private fun convertToCsv(measurements: List<PackageMeasurement>): String {
        val stringBuilder = StringBuilder()
        stringBuilder.appendLine(CSV_HEADER)

        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        measurements.forEach { measurement ->
            try {
                val timestamp = sdf.format(Date(measurement.timestamp))
                val packageName = escapeCsvValue(measurement.packageName)
                val declaredSize = escapeCsvValue(measurement.declaredSize)

                // Konversi dari meter ke sentimeter dengan 2 angka desimal
                val depthCm = String.format("%.2f", measurement.depth * 100)
                val widthCm = String.format("%.2f", measurement.width * 100)
                val heightCm = String.format("%.2f", measurement.height * 100)
                val volumeCm3 = String.format("%.2f", measurement.volume * 1_000_000)

                val categoryName = escapeCsvValue(measurement.packageSizeCategory)
                val imagePath = escapeCsvValue(measurement.imagePath ?: "N/A")
                val isValidated = if (measurement.isValidated) "Ya" else "Tidak"

                stringBuilder.appendLine(
                    "\"$timestamp\",\"$packageName\",\"$declaredSize\",$depthCm,$widthCm,$heightCm,$volumeCm3,\"$categoryName\",${measurement.estimatedPrice},\"$imagePath\",\"$isValidated\""
                )
            } catch (e: Exception) {
                Log.w(TAG, "Error converting measurement to CSV: ${measurement.id}", e)
                // Skip this measurement and continue with others
            }
        }

        return stringBuilder.toString()
    }

    /**
     * Escape CSV values to handle commas, quotes, and newlines
     */
    private fun escapeCsvValue(value: String): String {
        return value
            .replace("\"", "\"\"")  // Escape quotes by doubling them
            .replace("\n", " ")     // Replace newlines with spaces
            .replace("\r", " ")     // Replace carriage returns with spaces
    }

    /**
     * Mengekspor data ke file CSV dan membagikannya menggunakan share intent.
     */
    fun exportAndShare(context: Context, measurements: List<PackageMeasurement>, fileName: String) {
        if (measurements.isEmpty()) {
            Toast.makeText(context, "Tidak ada data untuk diekspor", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            Log.d(TAG, "Starting CSV export for ${measurements.size} measurements")

            val csvContent = convertToCsv(measurements)

            // Simpan file di cache directory agar tidak butuh permission storage
            val file = File(context.cacheDir, "$fileName.csv")

            // PERBAIKAN: Menggunakan FileOutputStream + OutputStreamWriter
            // yang kompatible dengan API 24, mengganti FileWriter(file, Charsets.UTF_8)
            // yang membutuhkan API 33+
            FileOutputStream(file).use { fileOutputStream ->
                OutputStreamWriter(fileOutputStream, StandardCharsets.UTF_8).use { writer ->
                    writer.write(csvContent)
                }
            }

            if (!file.exists() || file.length() == 0L) {
                throw Exception("File tidak berhasil dibuat atau kosong")
            }

            Log.d(TAG, "CSV file created: ${file.absolutePath}, size: ${file.length()} bytes")

            // Dapatkan URI yang aman menggunakan FileProvider
            val uri: Uri = try {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error getting FileProvider URI", e)
                throw Exception("Gagal membuat URI untuk file: ${e.message}")
            }

            // Buat Share Intent
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Ekspor Data Pengukuran AR: $fileName")
                putExtra(Intent.EXTRA_TEXT, "Data pengukuran paket dari aplikasi Paxel AR Scanner")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Bagikan File CSV")
            context.startActivity(chooser)

            Toast.makeText(context, "File CSV berhasil dibuat dan siap dibagikan", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "CSV export completed successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to export CSV", e)
            Toast.makeText(context, "Gagal mengekspor data: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Save CSV file to a specific location (for advanced usage)
     */
    fun saveCsvToFile(context: Context, measurements: List<PackageMeasurement>, filePath: String): Boolean {
        return try {
            val csvContent = convertToCsv(measurements)
            val file = File(filePath)

            file.parentFile?.mkdirs() // Create directories if they don't exist

            // PERBAIKAN: Menggunakan FileOutputStream + OutputStreamWriter
            // yang kompatible dengan API 24
            FileOutputStream(file).use { fileOutputStream ->
                OutputStreamWriter(fileOutputStream, StandardCharsets.UTF_8).use { writer ->
                    writer.write(csvContent)
                }
            }

            Log.d(TAG, "CSV saved to: $filePath")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving CSV to file: $filePath", e)
            false
        }
    }

    /**
     * Get CSV content as string (for testing or other uses)
     */
    fun getCsvContent(measurements: List<PackageMeasurement>): String {
        return convertToCsv(measurements)
    }

    /**
     * Validate measurements before export
     */
    fun validateMeasurements(measurements: List<PackageMeasurement>): List<String> {
        val issues = mutableListOf<String>()

        measurements.forEachIndexed { index, measurement ->
            if (!measurement.isValidMeasurement()) {
                issues.add("Pengukuran #${index + 1} (ID: ${measurement.id}) tidak valid")
            }
        }

        return issues
    }

    /**
     * Export measurements to CSV with validation
     */
    fun exportWithValidation(context: Context, measurements: List<PackageMeasurement>, fileName: String) {
        try {
            // Validate measurements first
            val validationIssues = validateMeasurements(measurements)

            if (validationIssues.isNotEmpty()) {
                Log.w(TAG, "Found ${validationIssues.size} validation issues")
                // Still proceed but log the issues
                validationIssues.forEach { issue ->
                    Log.w(TAG, "Validation issue: $issue")
                }
            }

            // Filter out completely invalid measurements
            val validMeasurements = measurements.filter { it.isValidMeasurement() }

            if (validMeasurements.isEmpty()) {
                Toast.makeText(context, "Tidak ada data valid untuk diekspor", Toast.LENGTH_LONG).show()
                return
            }

            if (validMeasurements.size != measurements.size) {
                val skippedCount = measurements.size - validMeasurements.size
                Toast.makeText(
                    context,
                    "Mengekspor ${validMeasurements.size} data valid (${skippedCount} data diabaikan)",
                    Toast.LENGTH_LONG
                ).show()
            }

            exportAndShare(context, validMeasurements, fileName)

        } catch (e: Exception) {
            Log.e(TAG, "Error during export with validation", e)
            Toast.makeText(context, "Gagal mengekspor data: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Create CSV header with custom fields
     */
    fun createCustomCsvHeader(includeImages: Boolean = true, includeValidation: Boolean = true): String {
        val baseHeader = "Timestamp,Nama Paket,Ukuran Dideklarasikan,Panjang (cm),Lebar (cm),Tinggi (cm),Volume (cm³),Kategori Ukuran,Estimasi Harga (Rp)"

        val additionalFields = mutableListOf<String>()
        if (includeImages) additionalFields.add("Path Gambar")
        if (includeValidation) additionalFields.add("Sudah Divalidasi")

        return if (additionalFields.isNotEmpty()) {
            "$baseHeader,${additionalFields.joinToString(",")}"
        } else {
            baseHeader
        }
    }

    /**
     * Get file size in human readable format
     */
    fun getFileSizeString(file: File): String {
        val sizeInBytes = file.length()
        return when {
            sizeInBytes < 1024 -> "$sizeInBytes B"
            sizeInBytes < 1024 * 1024 -> "${sizeInBytes / 1024} KB"
            else -> "${sizeInBytes / (1024 * 1024)} MB"
        }
    }
}