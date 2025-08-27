package com.paxel.arspacescan.ui.result

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paxel.arspacescan.data.model.MeasurementResult
import com.paxel.arspacescan.data.repository.MeasurementRepository
import com.paxel.arspacescan.data.mapper.*
import com.paxel.arspacescan.data.model.PackageMeasurement
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale

class MeasurementViewModel(private val repository: MeasurementRepository) : ViewModel() {

    private val _csvExportResult = MutableSharedFlow<String>()
    val csvExportResult = _csvExportResult.asSharedFlow()

    fun saveMeasurement(measurementResult: MeasurementResult) {
        viewModelScope.launch {
            repository.insertMeasurement(measurementResult)
        }
    }

    /**
     * Get measurement by ID as Flow for reactive UI updates
     */
    fun getMeasurementById(id: Long): Flow<MeasurementResult?> {
        return repository.getMeasurementById(id)
    }

    /**
     * Get all measurements as MeasurementResult for UI consistency
     */
    fun getAllMeasurements(): Flow<List<MeasurementResult>> {
        return repository.getAllMeasurements()
    }

    /**
     * Get all measurements as PackageMeasurement (direct from database)
     */
    fun getAllPackageMeasurements(): Flow<List<PackageMeasurement>> {
        return repository.getAllPackageMeasurements()
    }

    fun deleteMeasurementById(id: Long) {
        viewModelScope.launch {
            repository.deleteMeasurementById(id)
        }
    }

    fun deleteAllMeasurements() {
        viewModelScope.launch {
            repository.deleteAllMeasurements()
        }
    }

    fun updateMeasurement(measurementResult: MeasurementResult) {
        viewModelScope.launch {
            repository.updateMeasurement(measurementResult)
        }
    }

    fun exportToCSV() {
        viewModelScope.launch {
            try {
                val csvHeader = "ID,Package Name,Declared Size,Width(cm),Height(cm),Depth(cm),Volume(cm³),Timestamp,Has Photo,Validated,Category,Estimated Price\n"
                val csvContent = StringBuilder(csvHeader)
                val measurementList = repository.getAllPackageMeasurements().first()

                measurementList.forEach { measurement ->
                    csvContent.append("${measurement.id},")
                    csvContent.append("\"${measurement.packageName.replace("\"", "\"\"")}\",")
                    csvContent.append("\"${measurement.declaredSize.replace("\"", "\"\"")}\",")
                    csvContent.append("${String.format("%.2f", measurement.width * 100)},")
                    csvContent.append("${String.format("%.2f", measurement.height * 100)},")
                    csvContent.append("${String.format("%.2f", measurement.depth * 100)},")
                    csvContent.append("${String.format("%.2f", measurement.volume * 1_000_000)},")
                    csvContent.append("${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(java.util.Date(measurement.timestamp))},")
                    csvContent.append("${if (measurement.imagePath != null) "Yes" else "No"},")
                    csvContent.append("${measurement.isValidated},")
                    csvContent.append("\"${measurement.packageSizeCategory.replace("\"", "\"\"")}\",")
                    csvContent.append("${measurement.estimatedPrice}\n")
                }
                _csvExportResult.emit(csvContent.toString())
            } catch (e: Exception) {
                // Handle error - could emit error state or show error message
                _csvExportResult.emit("")
            }
        }
    }

    /**
     * Search measurements
     */
    fun searchMeasurements(query: String): Flow<List<MeasurementResult>> {
        return repository.searchMeasurements(query)
    }

    /**
     * Get measurements by category
     */
    fun getMeasurementsByCategory(category: String): Flow<List<MeasurementResult>> {
        return repository.getMeasurementsByCategory(category)
    }

    /**
     * Get measurements with photos
     */
    fun getMeasurementsWithPhotos(): Flow<List<MeasurementResult>> {
        return repository.getMeasurementsWithPhotos()
    }

    /**
     * Get measurement statistics
     */
    fun getMeasurementCount() = viewModelScope.launch {
        repository.getMeasurementCount()
    }

    fun getAverageVolume() = viewModelScope.launch {
        repository.getAverageVolume()
    }

    fun getAveragePrice() = viewModelScope.launch {
        repository.getAveragePrice()
    }
}