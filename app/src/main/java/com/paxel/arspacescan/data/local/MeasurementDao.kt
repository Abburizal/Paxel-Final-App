package com.paxel.arspacescan.data.local

import androidx.room.*
import com.paxel.arspacescan.data.model.PackageMeasurement
import kotlinx.coroutines.flow.Flow

@Dao
interface MeasurementDao {

    @Query("SELECT * FROM package_measurements ORDER BY timestamp DESC")
    fun getAllMeasurements(): Flow<List<PackageMeasurement>>

    @Query("SELECT * FROM package_measurements WHERE id = :id")
    suspend fun getMeasurementById(id: Long): PackageMeasurement?

    @Insert
    suspend fun insertMeasurement(measurement: PackageMeasurement): Long

    @Update
    suspend fun updateMeasurement(measurement: PackageMeasurement)

    @Delete
    suspend fun deleteMeasurement(measurement: PackageMeasurement)

    @Query("DELETE FROM package_measurements WHERE id = :id")
    suspend fun deleteMeasurementById(id: Long)

    @Query("DELETE FROM package_measurements")
    suspend fun deleteAllMeasurements()

    @Query("SELECT COUNT(*) FROM package_measurements")
    suspend fun getMeasurementCount(): Int

    @Query("SELECT * FROM package_measurements WHERE packageName LIKE :searchQuery ORDER BY timestamp DESC")
    fun searchMeasurements(searchQuery: String): Flow<List<PackageMeasurement>>
}
