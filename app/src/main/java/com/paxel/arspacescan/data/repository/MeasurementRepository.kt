package com.paxel.arspacescan.data.repository

import com.paxel.arspacescan.data.local.MeasurementDao
import com.paxel.arspacescan.data.model.PackageMeasurement
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MeasurementRepository(private val measurementDao: MeasurementDao) {

    val allMeasurements = measurementDao.getAllMeasurements()

    suspend fun insert(measurement: PackageMeasurement): Long {
        return measurementDao.insertMeasurement(measurement)
    }

    suspend fun delete(measurement: PackageMeasurement) {
        measurementDao.deleteMeasurement(measurement)
    }

    fun searchMeasurements(query: String): Flow<List<PackageMeasurement>> {
        return measurementDao.searchMeasurements("%$query%")
    }

    suspend fun getMeasurementById(id: Long): PackageMeasurement? {
        return measurementDao.getMeasurementById(id)
    }

    suspend fun exportToCSV(): String {
        val measurements = measurementDao.getAllMeasurementsForExport()
        val csvBuilder = StringBuilder()

        // CSV Header
        csvBuilder.append("ID,Nama Paket,Panjang (cm),Lebar (cm),Tinggi (cm),Volume (cm³),Berat Volumetrik (kg),Tanggal,Waktu\n")

        // CSV Data
        measurements.forEach { measurement ->
            val date = Date(measurement.timestamp)
            val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale("id", "ID"))
            val timeFormat = SimpleDateFormat("HH:mm:ss", Locale("id", "ID"))

            csvBuilder.append("${measurement.id},")
            csvBuilder.append("\"${measurement.packageName}\",") // Menggunakan kutip untuk menangani koma di nama paket
            csvBuilder.append("${measurement.length},")
            csvBuilder.append("${measurement.width},")
            csvBuilder.append("${measurement.height},")
            csvBuilder.append("${measurement.volume},")
            csvBuilder.append("${measurement.volumetricWeight},")
            csvBuilder.append("${dateFormat.format(date)},")
            csvBuilder.append("${timeFormat.format(date)}\n")
        }

        return csvBuilder.toString()
    }
}