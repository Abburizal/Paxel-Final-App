package com.paxel.arspacescan.data.local

import androidx.room.*
import com.paxel.arspacescan.data.model.PackageMeasurement
import kotlinx.coroutines.flow.Flow

@Dao
interface MeasurementDao {

    // ===== BASIC CRUD OPERATIONS =====

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(measurement: PackageMeasurement): Long

    @Update
    suspend fun update(measurement: PackageMeasurement): Int

    @Delete
    suspend fun delete(measurement: PackageMeasurement): Int

    @Query("DELETE FROM package_measurements WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("DELETE FROM package_measurements")
    suspend fun deleteAll(): Int

    // ===== QUERY OPERATIONS =====

    @Query("SELECT * FROM package_measurements ORDER BY timestamp DESC")
    fun getAllMeasurements(): Flow<List<PackageMeasurement>>

    @Query("SELECT * FROM package_measurements WHERE id = :id LIMIT 1")
    fun getMeasurementById(id: Long): Flow<PackageMeasurement?>

    @Query("SELECT * FROM package_measurements WHERE id = :id LIMIT 1")
    suspend fun getMeasurementByIdSync(id: Long): PackageMeasurement?

    @Query("SELECT * FROM package_measurements ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestMeasurement(): PackageMeasurement?

    @Query("SELECT COUNT(*) FROM package_measurements")
    suspend fun getMeasurementCount(): Int

    // ===== SEARCH & FILTER OPERATIONS =====

    @Query("""
        SELECT * FROM package_measurements 
        WHERE packageName LIKE :searchQuery 
           OR declaredSize LIKE :searchQuery
           OR packageSizeCategory LIKE :searchQuery
        ORDER BY timestamp DESC
    """)
    fun searchMeasurements(searchQuery: String): Flow<List<PackageMeasurement>>

    @Query("""
        SELECT * FROM package_measurements 
        WHERE timestamp BETWEEN :startTime AND :endTime 
        ORDER BY timestamp DESC
    """)
    fun getMeasurementsByDateRange(startTime: Long, endTime: Long): Flow<List<PackageMeasurement>>

    @Query("""
        SELECT * FROM package_measurements 
        WHERE packageSizeCategory = :category 
        ORDER BY timestamp DESC
    """)
    fun getMeasurementsByCategory(category: String): Flow<List<PackageMeasurement>>

    @Query("""
        SELECT * FROM package_measurements 
        WHERE estimatedPrice BETWEEN :minPrice AND :maxPrice 
        ORDER BY timestamp DESC
    """)
    fun getMeasurementsByPriceRange(minPrice: Int, maxPrice: Int): Flow<List<PackageMeasurement>>

    @Query("""
        SELECT * FROM package_measurements 
        WHERE imagePath IS NOT NULL AND imagePath != '' 
        ORDER BY timestamp DESC
    """)
    fun getMeasurementsWithPhotos(): Flow<List<PackageMeasurement>>

    @Query("""
        SELECT * FROM package_measurements 
        WHERE isValidated = :isValidated 
        ORDER BY timestamp DESC
    """)
    fun getMeasurementsByValidationStatus(isValidated: Boolean): Flow<List<PackageMeasurement>>

    // ===== ANALYTICS OPERATIONS =====

    @Query("SELECT DISTINCT packageSizeCategory FROM package_measurements ORDER BY packageSizeCategory")
    suspend fun getAllCategories(): List<String>

    @Query("SELECT AVG(volume) FROM package_measurements")
    suspend fun getAverageVolume(): Float?

    @Query("SELECT AVG(estimatedPrice) FROM package_measurements WHERE estimatedPrice > 0")
    suspend fun getAveragePrice(): Float?

    @Query("SELECT MIN(timestamp) FROM package_measurements")
    suspend fun getOldestMeasurementTimestamp(): Long?

    @Query("SELECT MAX(timestamp) FROM package_measurements")
    suspend fun getNewestMeasurementTimestamp(): Long?

    // ===== BATCH OPERATIONS =====

    @Query("SELECT * FROM package_measurements WHERE id IN (:ids)")
    suspend fun getMeasurementsByIds(ids: List<Long>): List<PackageMeasurement>

    @Query("DELETE FROM package_measurements WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(measurements: List<PackageMeasurement>): List<Long>

    // ===== VALIDATION QUERIES =====

    @Query("""
        SELECT COUNT(*) FROM package_measurements 
        WHERE packageName = :packageName 
          AND ABS(timestamp - :timestamp) < :timeWindowMs
    """)
    suspend fun checkDuplicateMeasurement(
        packageName: String,
        timestamp: Long,
        timeWindowMs: Long = 5000L
    ): Int

    @Query("""
        SELECT * FROM package_measurements 
        WHERE width <= 0 OR height <= 0 OR depth <= 0 OR volume <= 0
        ORDER BY timestamp DESC
    """)
    suspend fun getInvalidMeasurements(): List<PackageMeasurement>
}