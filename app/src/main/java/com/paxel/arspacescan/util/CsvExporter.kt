package com.paxel.arspacescan.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.paxel.arspacescan.data.model.PackageMeasurement
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Object singleton untuk menangani ekspor data pengukuran ke file CSV.
 */
object CsvExporter {

    private const val CSV_HEADER = "Timestamp,Nama Paket,Ukuran Dideklarasikan,Panjang (cm),Lebar (cm),Tinggi (cm),Volume (cm³),Kategori Ukuran,Estimasi Harga (Rp),Path Gambar"

    /**
     * Mengubah daftar data pengukuran menjadi format string CSV.
     * @param measurements Daftar data yang akan diekspor.
     * @return String dengan format CSV.
     */
    private fun convertToCsv(measurements: List<PackageMeasurement>): String {
        val stringBuilder = StringBuilder()
        stringBuilder.appendLine(CSV_HEADER)

        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        measurements.forEach { measurement ->
            val timestamp = sdf.format(Date(measurement.timestamp))
            val packageName = measurement.packageName.replace(",", "")
            val declaredSize = measurement.declaredSize.replace(",", "")
            // Konversi dari meter ke sentimeter dengan 2 angka desimal
            val depthCm = String.format("%.2f", measurement.depth * 100)
            val widthCm = String.format("%.2f", measurement.width * 100)
            val heightCm = String.format("%.2f", measurement.height * 100)
            val volumeCm3 = String.format("%.2f", measurement.volume * 1_000_000)
            val imagePath = measurement.imagePath ?: "N/A"

            stringBuilder.appendLine(
                "\"${timestamp}\",\"${packageName}\",\"${declaredSize}\",${depthCm},${widthCm},${heightCm},${volumeCm3},\"${measurement.packageSizeCategory}\",${measurement.estimatedPrice},\"${imagePath}\""
            )
        }
        return stringBuilder.toString()
    }

    /**
     * Mengekspor data ke file CSV dan membagikannya menggunakan share intent.
     * @param context Context aplikasi.
     * @param measurements Daftar data yang akan diekspor.
     * @param fileName Nama file CSV yang akan dibuat (tanpa ekstensi).
     */
    fun exportAndShare(context: Context, measurements: List<PackageMeasurement>, fileName: String) {
        if (measurements.isEmpty()) {
            Toast.makeText(context, "Tidak ada data untuk diekspor", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val csvContent = convertToCsv(measurements)
            // Simpan file di cache directory agar tidak butuh permission storage
            val file = File(context.cacheDir, "$fileName.csv")
            FileWriter(file).use {
                it.write(csvContent)
            }

            // Dapatkan URI yang aman menggunakan FileProvider
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )

            // Buat Share Intent
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Ekspor Data Pengukuran: $fileName")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(Intent.createChooser(shareIntent, "Bagikan File CSV"))

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Gagal mengekspor data: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}