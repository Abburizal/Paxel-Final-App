package com.paxel.arspacescan.ui.result

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paxel.arspacescan.data.model.MeasurementResult
import com.paxel.arspacescan.data.model.PackageMeasurement
import com.paxel.arspacescan.data.repository.MeasurementRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MeasurementViewModel(private val repository: MeasurementRepository) : ViewModel() {

    suspend fun saveMeasurement(measurementResult: MeasurementResult): Long {
        // Convert MeasurementResult to PackageMeasurement for database storage
        val packageMeasurement = PackageMeasurement(
            id = measurementResult.id,
            packageName = measurementResult.packageName ?: "",
            declaredSize = measurementResult.declaredSize ?: "",
            measuredWidth = measurementResult.width,
            measuredHeight = measurementResult.height,
            measuredDepth = measurementResult.depth,
            measuredVolume = measurementResult.volume,
            timestamp = measurementResult.timestamp,
            isValidated = true
        )
        return repository.insert(packageMeasurement)
    }

    fun getMeasurementById(id: Long): Flow<MeasurementResult?> {
        return repository.getAllMeasurements().map { measurements ->
            measurements.find { it.id == id }?.let { packageMeasurement ->
                // Convert PackageMeasurement to MeasurementResult
                MeasurementResult(
                    id = packageMeasurement.id,
                    width = packageMeasurement.measuredWidth,
                    height = packageMeasurement.measuredHeight,
                    depth = packageMeasurement.measuredDepth,
                    volume = packageMeasurement.measuredVolume,
                    timestamp = packageMeasurement.timestamp,
                    packageName = packageMeasurement.packageName,
                    declaredSize = packageMeasurement.declaredSize
                )
            }
        }
    }

    fun getAllMeasurements(): Flow<List<MeasurementResult>> {
        return repository.getAllMeasurements().map { packageMeasurements ->
            packageMeasurements.map { packageMeasurement ->
                MeasurementResult(
                    id = packageMeasurement.id,
                    width = packageMeasurement.measuredWidth,
                    height = packageMeasurement.measuredHeight,
                    depth = packageMeasurement.measuredDepth,
                    volume = packageMeasurement.measuredVolume,
                    timestamp = packageMeasurement.timestamp,
                    packageName = packageMeasurement.packageName,
                    declaredSize = packageMeasurement.declaredSize
                )
            }
        }
    }

    suspend fun deleteMeasurementById(id: Long) {
        val packageMeasurement = repository.getMeasurementById(id)
        packageMeasurement?.let {
            repository.delete(it)
        }
    }

    suspend fun updateMeasurement(measurementResult: MeasurementResult) {
        val packageMeasurement = PackageMeasurement(
            id = measurementResult.id,
            packageName = measurementResult.packageName ?: "",
            declaredSize = measurementResult.declaredSize ?: "",
            measuredWidth = measurementResult.width,
            measuredHeight = measurementResult.height,
            measuredDepth = measurementResult.depth,
            measuredVolume = measurementResult.volume,
            timestamp = measurementResult.timestamp,
            isValidated = true
        )
        repository.update(packageMeasurement)
    }

    suspend fun exportToCSV(): String {
        val measurements = repository.getAllMeasurements()
        val csvHeader = "ID,Package Name,Declared Size,Width(cm),Height(cm),Depth(cm),Volume(cm³),Timestamp,Validated\n"

        val csvContent = StringBuilder(csvHeader)

        // Convert Flow to List for CSV export
        measurements.collect { measurementList ->
            measurementList.forEach { measurement ->
                csvContent.append("${measurement.id},")
                csvContent.append("\"${measurement.packageName}\",")
                csvContent.append("\"${measurement.declaredSize}\",")
                csvContent.append("${measurement.measuredWidth * 100},") // Convert to cm
                csvContent.append("${measurement.measuredHeight * 100},")
                csvContent.append("${measurement.measuredDepth * 100},")
                csvContent.append("${measurement.measuredVolume * 1000000},") // Convert to cm³
                csvContent.append("${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date(measurement.timestamp))},")
                csvContent.append("${measurement.isValidated}\n")
            }
        }

        return csvContent.toString()
    }
}
