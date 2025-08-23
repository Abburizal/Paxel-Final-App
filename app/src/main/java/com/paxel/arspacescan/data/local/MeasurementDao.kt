package com.paxel.arspacescan.data.local

import androidx.room.*
import com.paxel.arspacescan.data.model.PackageMeasurement
import kotlinx.coroutines.flow.Flow

@Dao
interface MeasurementDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(measurement: PackageMeasurement): Long

    @Update
    suspend fun update(measurement: PackageMeasurement)

    // Tambahkan anotasi @Delete
    @Delete
    suspend fun delete(measurement: PackageMeasurement) // Tambahkan fungsi ini

    @Query("SELECT * FROM package_measurements ORDER BY timestamp DESC")
    fun getAllMeasurements(): Flow<List<PackageMeasurement>>

    // [PERBAIKAN] Ubah fungsi ini untuk mengembalikan Flow agar bisa diobservasi
    @Query("SELECT * FROM package_measurements WHERE id = :id")
    fun getMeasurementById(id: Long): Flow<PackageMeasurement?>

    @Query("DELETE FROM package_measurements WHERE id = :id")
    suspend fun deleteMeasurementById(id: Long)

    @Query("DELETE FROM package_measurements")
    suspend fun deleteAll()
}