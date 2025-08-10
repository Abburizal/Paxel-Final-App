package com.paxel.arspacescan.data.local

import androidx.room.*
import com.paxel.arspacescan.data.model.PackageMeasurement
import kotlinx.coroutines.flow.Flow

@Dao
interface MeasurementDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMeasurement(measurement: PackageMeasurement): Long

    @Query("SELECT * FROM package_measurements ORDER BY timestamp DESC")
    fun getAllMeasurements(): Flow<List<PackageMeasurement>>

    @Query("SELECT * FROM package_measurements WHERE package_name LIKE :searchQuery ORDER BY timestamp DESC")
    fun searchMeasurements(searchQuery: String): Flow<List<PackageMeasurement>>

    @Query("SELECT * FROM package_measurements WHERE id = :id LIMIT 1")
    suspend fun getMeasurementById(id: Long): PackageMeasurement?

    @Delete
    suspend fun deleteMeasurement(measurement: PackageMeasurement): Int

    @Query("DELETE FROM package_measurements")
    suspend fun deleteAllMeasurements(): Int

    @Query("SELECT * FROM package_measurements ORDER BY timestamp DESC")
    suspend fun getAllMeasurementsForExport(): List<PackageMeasurement>
}