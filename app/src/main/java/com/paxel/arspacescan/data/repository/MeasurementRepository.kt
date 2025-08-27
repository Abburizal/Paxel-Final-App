package com.paxel.arspacescan.data.repository

import android.util.Log
import com.paxel.arspacescan.data.local.MeasurementDao
import com.paxel.arspacescan.data.model.PackageMeasurement
import com.paxel.arspacescan.data.model.MeasurementResult
import com.paxel.arspacescan.data.mapper.toPackageMeasurement
import com.paxel.arspacescan.data.mapper.toMeasurementResult
import com.paxel.arspacescan.data.mapper.toMeasurementResults
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.catch

/**
 * Repository layer untuk measurement data
 */
class MeasurementRepository(private val measurementDao: MeasurementDao) {

    companion object {
        private const val TAG = "MeasurementRepository"
    }

    // ===== BASIC CRUD OPERATIONS =====

    /**
     * Insert measurement result ke database
     * Returns: ID of inserted measurement, or -1 if failed
     */
    suspend fun insertMeasurement(measurementResult: MeasurementResult): Long {
        return try {
            // Validate before insert
            require(measurementResult.isValid()) { "Invalid measurement data" }

            // Check for duplicates
            val duplicateCount = measurementDao.checkDuplicateMeasurement(
                measurementResult.packageName,
                measurementResult.timestamp
            )

            if (duplicateCount > 0) {
                Log.w(TAG, "Potential duplicate measurement detected")
            }

            val packageMeasurement = measurementResult.toPackageMeasurement()
            val insertedId = measurementDao.insert(packageMeasurement)

            Log.d(TAG, "Measurement inserted successfully with ID: $insertedId")
            insertedId

        } catch (e: Exception) {
            Log.e(TAG, "Failed to insert measurement", e)
            -1L
        }
    }

    /**
     * Insert PackageMeasurement directly (legacy support)
     */
    suspend fun insert(measurement: PackageMeasurement): Long {
        return try {
            measurementDao.insert(measurement)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to insert PackageMeasurement", e)
            -1L
        }
    }

    /**
     * Update existing measurement
     */
    suspend fun updateMeasurement(measurementResult: MeasurementResult): Boolean {
        return try {
            require(measurementResult.isValid()) { "Invalid measurement data" }
            require(measurementResult.id > 0) { "Invalid measurement ID for update" }

            val packageMeasurement = measurementResult.toPackageMeasurement()
            val rowsUpdated = measurementDao.update(packageMeasurement)

            Log.d(TAG, "Measurement updated: $rowsUpdated rows affected")
            rowsUpdated > 0

        } catch (e: Exception) {
            Log.e(TAG, "Failed to update measurement", e)
            false
        }
    }

    /**
     * Update PackageMeasurement directly (legacy support)
     */
    suspend fun update(measurement: PackageMeasurement): Int {
        return try {
            measurementDao.update(measurement)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update PackageMeasurement", e)
            0
        }
    }

    /**
     * Delete measurement by ID
     */
    suspend fun deleteMeasurementById(id: Long): Boolean {
        return try {
            require(id > 0) { "Invalid measurement ID" }

            val rowsDeleted = measurementDao.deleteById(id)

            Log.d(TAG, "Measurement deleted: $rowsDeleted rows affected")
            rowsDeleted > 0

        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete measurement by ID: $id", e)
            false
        }
    }

    /**
     * Delete measurement by object
     */
    suspend fun deleteMeasurement(measurementResult: MeasurementResult): Boolean {
        return try {
            val packageMeasurement = measurementResult.toPackageMeasurement()
            val rowsDeleted = measurementDao.delete(packageMeasurement)

            Log.d(TAG, "Measurement deleted: $rowsDeleted rows affected")
            rowsDeleted > 0

        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete measurement", e)
            false
        }
    }

    /**
     * Delete PackageMeasurement directly (legacy support)
     */
    suspend fun delete(measurement: PackageMeasurement): Int {
        return try {
            measurementDao.delete(measurement)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete PackageMeasurement", e)
            0
        }
    }

    /**
     * Delete all measurements
     */
    suspend fun deleteAllMeasurements(): Boolean {
        return try {
            val rowsDeleted = measurementDao.deleteAll()

            Log.d(TAG, "All measurements deleted: $rowsDeleted rows affected")
            rowsDeleted > 0

        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete all measurements", e)
            false
        }
    }

    // ===== QUERY OPERATIONS =====

    /**
     * Get all measurements as Flow untuk reactive UI
     */
    fun getAllMeasurements(): Flow<List<MeasurementResult>> {
        return measurementDao.getAllMeasurements()
            .map { packageMeasurements ->
                packageMeasurements.toMeasurementResults()
            }
            .catch { e ->
                Log.e(TAG, "Failed to get all measurements", e)
                emit(emptyList())
            }
    }

    /**
     * Get all measurements as PackageMeasurement (direct from DB)
     */
    fun getAllPackageMeasurements(): Flow<List<PackageMeasurement>> {
        return measurementDao.getAllMeasurements()
            .catch { e ->
                Log.e(TAG, "Failed to get all PackageMeasurements", e)
                emit(emptyList())
            }
    }

    /**
     * Get measurement by ID as Flow
     */
    fun getMeasurementById(id: Long): Flow<MeasurementResult?> {
        return measurementDao.getMeasurementById(id)
            .map { packageMeasurement ->
                packageMeasurement?.toMeasurementResult()
            }
            .catch { e ->
                Log.e(TAG, "Failed to get measurement by ID: $id", e)
                emit(null)
            }
    }

    /**
     * Get measurement by ID synchronously
     */
    suspend fun getMeasurementByIdSync(id: Long): MeasurementResult? {
        return try {
            measurementDao.getMeasurementByIdSync(id)?.toMeasurementResult()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get measurement sync by ID: $id", e)
            null
        }
    }

    /**
     * Get latest measurement
     */
    suspend fun getLatestMeasurement(): MeasurementResult? {
        return try {
            measurementDao.getLatestMeasurement()?.toMeasurementResult()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get latest measurement", e)
            null
        }
    }

    /**
     * Get measurement count
     */
    suspend fun getMeasurementCount(): Int {
        return try {
            measurementDao.getMeasurementCount()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get measurement count", e)
            0
        }
    }

    // ===== SEARCH & FILTER OPERATIONS =====

    /**
     * Search measurements by query
     */
    fun searchMeasurements(query: String): Flow<List<MeasurementResult>> {
        val searchQuery = "%$query%"
        return measurementDao.searchMeasurements(searchQuery)
            .map { packageMeasurements ->
                packageMeasurements.toMeasurementResults()
            }
            .catch { e ->
                Log.e(TAG, "Failed to search measurements with query: $query", e)
                emit(emptyList())
            }
    }

    /**
     * Get measurements by date range
     */
    fun getMeasurementsByDateRange(startTime: Long, endTime: Long): Flow<List<MeasurementResult>> {
        return measurementDao.getMeasurementsByDateRange(startTime, endTime)
            .map { packageMeasurements ->
                packageMeasurements.toMeasurementResults()
            }
            .catch { e ->
                Log.e(TAG, "Failed to get measurements by date range", e)
                emit(emptyList())
            }
    }

    /**
     * Get measurements by category
     */
    fun getMeasurementsByCategory(category: String): Flow<List<MeasurementResult>> {
        return measurementDao.getMeasurementsByCategory(category)
            .map { packageMeasurements ->
                packageMeasurements.toMeasurementResults()
            }
            .catch { e ->
                Log.e(TAG, "Failed to get measurements by category: $category", e)
                emit(emptyList())
            }
    }

    /**
     * Get measurements by price range
     */
    fun getMeasurementsByPriceRange(minPrice: Int, maxPrice: Int): Flow<List<MeasurementResult>> {
        return measurementDao.getMeasurementsByPriceRange(minPrice, maxPrice)
            .map { packageMeasurements ->
                packageMeasurements.toMeasurementResults()
            }
            .catch { e ->
                Log.e(TAG, "Failed to get measurements by price range", e)
                emit(emptyList())
            }
    }

    /**
     * Get measurements with photos
     */
    fun getMeasurementsWithPhotos(): Flow<List<MeasurementResult>> {
        return measurementDao.getMeasurementsWithPhotos()
            .map { packageMeasurements ->
                packageMeasurements.toMeasurementResults()
            }
            .catch { e ->
                Log.e(TAG, "Failed to get measurements with photos", e)
                emit(emptyList())
            }
    }

    // ===== ANALYTICS OPERATIONS =====

    suspend fun getAllCategories(): List<String> {
        return try {
            measurementDao.getAllCategories()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get categories", e)
            emptyList()
        }
    }

    suspend fun getAverageVolume(): Float? {
        return try {
            measurementDao.getAverageVolume()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get average volume", e)
            null
        }
    }

    suspend fun getAveragePrice(): Float? {
        return try {
            measurementDao.getAveragePrice()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get average price", e)
            null
        }
    }

    // ===== VALIDATION OPERATIONS =====

    suspend fun validateDatabase(): List<MeasurementResult> {
        return try {
            val invalidMeasurements = measurementDao.getInvalidMeasurements()
            invalidMeasurements.toMeasurementResults()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to validate database", e)
            emptyList()
        }
    }
}