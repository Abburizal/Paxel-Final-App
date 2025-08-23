package com.paxel.arspacescan.ui.result

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paxel.arspacescan.data.model.MeasurementResult
import com.paxel.arspacescan.data.model.PackageMeasurement
import com.paxel.arspacescan.data.repository.MeasurementRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Locale

class MeasurementViewModel(private val repository: MeasurementRepository) : ViewModel() {

    private val _csvExportResult = MutableSharedFlow<String>()
    val csvExportResult = _csvExportResult.asSharedFlow()

    fun saveMeasurement(measurementResult: MeasurementResult) {
        viewModelScope.launch {
            repository.insert(measurementResult.toPackageMeasurement())
        }
    }

    // [PERBAIKAN] Fungsi ini tidak lagi 'suspend' dan mengembalikan 'Flow'.
    // Ini adalah cara yang benar untuk mengobservasi data tunggal dari database.
    fun getMeasurementById(id: Long): Flow<MeasurementResult?> {
        return repository.getMeasurementById(id).map { it?.toMeasurementResult() }
    }

    fun getAllMeasurements(): Flow<List<MeasurementResult>> {
        return repository.getAllMeasurements().map { packageMeasurements ->
            packageMeasurements.map { it.toMeasurementResult() }
        }
    }

    fun deleteMeasurementById(id: Long) {
        viewModelScope.launch {
            repository.deleteMeasurementById(id)
        }
    }

    // [PERBAIKAN] Tambahkan fungsi ini untuk HistoryActivity
    fun deleteAllMeasurements() {
        viewModelScope.launch {
            repository.deleteAllMeasurements()
        }
    }

    fun updateMeasurement(measurementResult: MeasurementResult) {
        viewModelScope.launch {
            repository.update(measurementResult.toPackageMeasurement())
        }
    }

    fun exportToCSV() {
        viewModelScope.launch {
            val csvHeader = "ID,Package Name,Declared Size,Width(cm),Height(cm),Depth(cm),Volume(cm³),Timestamp,Validated\n"
            val csvContent = StringBuilder(csvHeader)
            val measurementList = repository.getAllMeasurements().first()

            measurementList.forEach { measurement ->
                csvContent.append("${measurement.id},")
                csvContent.append("\"${measurement.packageName}\",")
                csvContent.append("\"${measurement.declaredSize}\",")
                csvContent.append("${measurement.measuredWidth * 100},")
                csvContent.append("${measurement.measuredHeight * 100},")
                csvContent.append("${measurement.measuredDepth * 100},")
                csvContent.append("${measurement.measuredVolume * 1_000_000},")
                csvContent.append("${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(java.util.Date(measurement.timestamp))},")
                csvContent.append("${measurement.isValidated}\n")
            }
            _csvExportResult.emit(csvContent.toString())
        }
    }

    // --- Extension Functions untuk Konversi Data ---

    private fun PackageMeasurement.toMeasurementResult(): MeasurementResult {
        return MeasurementResult(
            id = this.id,
            width = this.measuredWidth,
            height = this.measuredHeight,
            depth = this.measuredDepth,
            volume = this.measuredVolume,
            timestamp = this.timestamp,
            packageName = this.packageName,
            declaredSize = this.declaredSize
        )
    }

    private fun MeasurementResult.toPackageMeasurement(): PackageMeasurement {
        return PackageMeasurement(
            id = this.id,
            packageName = this.packageName ?: "",
            declaredSize = this.declaredSize ?: "",
            measuredWidth = this.width,
            measuredHeight = this.height,
            measuredDepth = this.depth,
            measuredVolume = this.volume,
            timestamp = this.timestamp,
            isValidated = true
        )
    }
}