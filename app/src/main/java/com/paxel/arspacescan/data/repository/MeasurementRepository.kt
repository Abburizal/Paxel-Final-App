package com.paxel.arspacescan.data.repository

import com.paxel.arspacescan.data.local.MeasurementDao
import com.paxel.arspacescan.data.model.PackageMeasurement
import kotlinx.coroutines.flow.Flow

class MeasurementRepository(private val measurementDao: MeasurementDao) {

    suspend fun insert(measurement: PackageMeasurement) = measurementDao.insert(measurement)
    suspend fun update(measurement: PackageMeasurement) = measurementDao.update(measurement)
    suspend fun deleteMeasurementById(id: Long) = measurementDao.deleteMeasurementById(id)
    suspend fun deleteAllMeasurements() = measurementDao.deleteAll()

    fun getAllMeasurements(): Flow<List<PackageMeasurement>> = measurementDao.getAllMeasurements()

    // [PERBAIKAN] Pastikan fungsi ini meneruskan Flow dari DAO
    fun getMeasurementById(id: Long): Flow<PackageMeasurement?> {
        return measurementDao.getMeasurementById(id)
    }
}