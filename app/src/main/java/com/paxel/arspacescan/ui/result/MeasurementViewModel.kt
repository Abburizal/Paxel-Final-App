package com.paxel.arspacescan.ui.result

import androidx.lifecycle.*
import com.paxel.arspacescan.data.model.PackageMeasurement
import com.paxel.arspacescan.data.repository.MeasurementRepository
import kotlinx.coroutines.launch

class MeasurementViewModel(private val repository: MeasurementRepository) : ViewModel() {

    val allMeasurements = repository.allMeasurements.asLiveData()
    private val searchQuery = MutableLiveData<String>("")

    val filteredMeasurements: LiveData<List<PackageMeasurement>> = searchQuery.switchMap { query ->
        if (query.isNullOrEmpty()) {
            repository.allMeasurements.asLiveData()
        } else {
            repository.searchMeasurements(query).asLiveData()
        }
    }

    suspend fun insert(measurement: PackageMeasurement): Long {
        return repository.insert(measurement)
    }

    fun delete(measurement: PackageMeasurement) = viewModelScope.launch {
        repository.delete(measurement)
    }

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    suspend fun exportToCSV(): String {
        return repository.exportToCSV()
    }

    suspend fun getMeasurementById(id: Long): PackageMeasurement? {
        return repository.getMeasurementById(id)
    }
}