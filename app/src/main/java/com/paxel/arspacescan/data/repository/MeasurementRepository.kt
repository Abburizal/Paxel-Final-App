package com.paxel.arspacescan.data.repository

import com.paxel.arspacescan.data.local.MeasurementDao
import com.paxel.arspacescan.data.model.PackageMeasurement
import kotlinx.coroutines.flow.Flow

class MeasurementRepository(private val measurementDao: MeasurementDao) {

    fun getAllMeasurements(): Flow<List<PackageMeasurement>> {
        return measurementDao.getAllMeasurements()
    }

    suspend fun getMeasurementById(id: Long): PackageMeasurement? {
        return measurementDao.getMeasurementById(id)
    }

    suspend fun insert(measurement: PackageMeasurement): Long {
        return measurementDao.insertMeasurement(measurement)
    }

    suspend fun update(measurement: PackageMeasurement) {
        measurementDao.updateMeasurement(measurement)
    }

    suspend fun delete(measurement: PackageMeasurement) {
        measurementDao.deleteMeasurement(measurement)
    }

    suspend fun deleteAll() {
        measurementDao.deleteAllMeasurements()
    }

    suspend fun getMeasurementCount(): Int {
        return measurementDao.getMeasurementCount()
    }
}
