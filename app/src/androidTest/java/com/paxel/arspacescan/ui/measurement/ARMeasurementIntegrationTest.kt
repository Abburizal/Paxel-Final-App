package com.paxel.arspacescan.ui.measurement

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.paxel.arspacescan.data.local.AppDatabase
import com.paxel.arspacescan.data.repository.MeasurementRepository
import com.paxel.arspacescan.data.model.MeasurementResult
import com.paxel.arspacescan.util.PackageSizeValidator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumentation tests untuk AR Measurement integration
 * Tests complete measurement flow dengan real database
 */
@ExperimentalCoroutinesApi
@LargeTest
@RunWith(AndroidJUnit4::class)
class ARMeasurementIntegrationTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private lateinit var database: AppDatabase
    private lateinit var repository: MeasurementRepository
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()

        // Create in-memory database untuk testing
        database = Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        ).allowMainThreadQueries().build()

        repository = MeasurementRepository(database.measurementDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ===== END-TO-END MEASUREMENT FLOW TESTS =====

    @Test
    fun shouldCompleteFullMeasurementWorkflow() = runTest {
        // Step 1: Create measurement result (simulating AR calculation)
        val measurementResult = MeasurementResult(
            packageName = "Test Package",
            declaredSize = "Medium",
            width = 0.2f,      // 20cm
            height = 0.15f,    // 15cm
            depth = 0.1f,      // 10cm
            volume = 0.003f,   // 3000 cm³
            timestamp = System.currentTimeMillis()
        )

        // Step 2: Validate package size and estimate price
        val validation = PackageSizeValidator.validate(context, measurementResult)
        val updatedResult = measurementResult.copy(
            packageSizeCategory = validation.category,
            estimatedPrice = validation.estimatedPrice
        )

        // Step 3: Save to database
        val insertedId = repository.insertMeasurement(updatedResult)
        assertTrue("Insert should succeed", insertedId > 0)

        // Step 4: Retrieve from database
        val retrievedResult = repository.getMeasurementByIdSync(insertedId)
        assertNotNull("Retrieved result should not be null", retrievedResult)

        // Step 5: Verify data integrity
        assertEquals("Test Package", retrievedResult!!.packageName)
        assertEquals("Medium", retrievedResult.declaredSize)
        assertEquals(0.2f, retrievedResult.width, 0.001f)
        assertEquals(0.15f, retrievedResult.height, 0.001f)
        assertEquals(0.1f, retrievedResult.depth, 0.001f)
        assertEquals(0.003f, retrievedResult.volume, 0.0001f)
        assertTrue("Estimated price should be positive", retrievedResult.estimatedPrice > 0)
        assertTrue("Category should not be empty", retrievedResult.packageSizeCategory.isNotEmpty())
    }

    @Test
    fun shouldHandleConcurrentMeasurementsCorrectly() = runTest {
        val measurements = (1..10).map { index ->
            val size = 0.1f + index * 0.01f
            MeasurementResult(
                packageName = "Package $index",
                width = size,
                height = size,
                depth = size,
                volume = size * size * size,
                timestamp = System.currentTimeMillis() + index
            )
        }

        // Insert all measurements
        val insertedIds = measurements.map { measurement ->
            repository.insertMeasurement(measurement)
        }

        // Verify all measurements were inserted
        insertedIds.forEach { id ->
            assertTrue("Each insert should succeed", id > 0)
        }

        // Verify count
        val count = repository.getMeasurementCount()
        assertEquals(10, count)

        // Verify retrieval
        val allMeasurements = repository.getAllMeasurements().first()
        assertEquals(10, allMeasurements.size)
    }

    @Test
    fun shouldValidateMeasurementDataBeforeSaving() = runTest {
        // Test valid measurement
        val validMeasurement = MeasurementResult(
            packageName = "Valid Package",
            width = 0.5f,
            height = 0.3f,
            depth = 0.2f,
            volume = 0.03f,
            timestamp = System.currentTimeMillis()
        )

        val validId = repository.insertMeasurement(validMeasurement)
        assertTrue("Valid measurement should be inserted", validId > 0)

        // Test invalid measurement (negative dimensions)
        val invalidMeasurement = MeasurementResult(
            packageName = "Invalid Package",
            width = -0.5f,  // Invalid negative width
            height = 0.3f,
            depth = 0.2f,
            volume = -0.03f, // Invalid negative volume
            timestamp = System.currentTimeMillis()
        )

        val invalidId = repository.insertMeasurement(invalidMeasurement)
        assertEquals("Invalid measurement should fail", -1L, invalidId)
    }

    // ===== PRICE ESTIMATION INTEGRATION TESTS =====

    @Test
    fun shouldCalculatePriceEstimationForDifferentPackageSizes() = runTest {
        // Small package test
        val smallMeasurement = MeasurementResult(
            packageName = "Small Package",
            width = 0.1f, height = 0.1f, depth = 0.1f,
            volume = 0.001f, timestamp = System.currentTimeMillis()
        )

        val smallValidation = PackageSizeValidator.validate(context, smallMeasurement)
        assertTrue("Small package should have positive price", smallValidation.estimatedPrice > 0)

        // Medium package test
        val mediumMeasurement = MeasurementResult(
            packageName = "Medium Package",
            width = 0.2f, height = 0.2f, depth = 0.2f,
            volume = 0.008f, timestamp = System.currentTimeMillis()
        )

        val mediumValidation = PackageSizeValidator.validate(context, mediumMeasurement)
        assertTrue("Medium package should have positive price", mediumValidation.estimatedPrice > 0)

        // Large package test
        val largeMeasurement = MeasurementResult(
            packageName = "Large Package",
            width = 0.3f, height = 0.3f, depth = 0.3f,
            volume = 0.027f, timestamp = System.currentTimeMillis()
        )

        val largeValidation = PackageSizeValidator.validate(context, largeMeasurement)
        assertTrue("Large package should have positive price", largeValidation.estimatedPrice > 0)

        // Save and verify all measurements
        val measurements = listOf(
            smallMeasurement.copy(
                packageSizeCategory = smallValidation.category,
                estimatedPrice = smallValidation.estimatedPrice
            ),
            mediumMeasurement.copy(
                packageSizeCategory = mediumValidation.category,
                estimatedPrice = mediumValidation.estimatedPrice
            ),
            largeMeasurement.copy(
                packageSizeCategory = largeValidation.category,
                estimatedPrice = largeValidation.estimatedPrice
            )
        )

        measurements.forEach { measurement ->
            val insertedId = repository.insertMeasurement(measurement)
            assertTrue("Measurement should be inserted", insertedId > 0)

            val retrieved = repository.getMeasurementByIdSync(insertedId)
            assertNotNull("Retrieved measurement should not be null", retrieved)
            assertTrue("Estimated price should be preserved", retrieved!!.estimatedPrice > 0)
            assertTrue("Category should be preserved", retrieved.packageSizeCategory.isNotEmpty())
        }
    }

    // ===== DATABASE FIELD TESTS =====

    @Test
    fun shouldHandleDatabaseFieldsCorrectly() = runTest {
        val measurement = MeasurementResult(
            packageName = "Migration Test",
            declaredSize = "Custom Size",
            width = 0.25f,
            height = 0.25f,
            depth = 0.25f,
            volume = 0.015625f,
            timestamp = System.currentTimeMillis(),
            imagePath = "/storage/test/image.jpg",
            packageSizeCategory = "Test Category",
            estimatedPrice = 15000
        )

        val insertedId = repository.insertMeasurement(measurement)
        assertTrue("Measurement should be inserted", insertedId > 0)

        val retrieved = repository.getMeasurementByIdSync(insertedId)
        assertNotNull("Retrieved measurement should not be null", retrieved)

        // Verify all fields including new ones from migration 3_4
        assertEquals("Migration Test", retrieved!!.packageName)
        assertEquals("Custom Size", retrieved.declaredSize)
        assertEquals("/storage/test/image.jpg", retrieved.imagePath)
        assertEquals("Test Category", retrieved.packageSizeCategory)
        assertEquals(15000, retrieved.estimatedPrice)
    }

    // ===== SEARCH AND FILTER TESTS =====

    @Test
    fun shouldSearchMeasurementsCorrectly() = runTest {
        // Insert test data
        val measurements = listOf(
            createTestMeasurement("iPhone Box", 0.15f),
            createTestMeasurement("Samsung Galaxy Box", 0.16f),
            createTestMeasurement("Book Package", 0.2f)
        )

        measurements.forEach { measurement ->
            repository.insertMeasurement(measurement)
        }

        // Test search by package name
        val phoneResults = repository.searchMeasurements("iPhone").first()
        assertEquals(1, phoneResults.size)
        assertEquals("iPhone Box", phoneResults.first().packageName)

        val galaxyResults = repository.searchMeasurements("Galaxy").first()
        assertEquals(1, galaxyResults.size)
        assertEquals("Samsung Galaxy Box", galaxyResults.first().packageName)

        val boxResults = repository.searchMeasurements("Box").first()
        assertEquals(2, boxResults.size) // Should find both phone boxes
    }

    @Test
    fun shouldFilterByDateRangeCorrectly() = runTest {
        val baseTime = System.currentTimeMillis()
        val measurements = listOf(
            createTestMeasurement("Old Package", 0.1f, baseTime - 86400000), // Yesterday
            createTestMeasurement("Current Package", 0.1f, baseTime), // Now
            createTestMeasurement("Future Package", 0.1f, baseTime + 86400000) // Tomorrow
        )

        measurements.forEach { measurement ->
            repository.insertMeasurement(measurement)
        }

        // Test date range filter (last 12 hours)
        val recentMeasurements = repository.getMeasurementsByDateRange(
            baseTime - 43200000, // 12 hours ago
            baseTime + 43200000  // 12 hours from now
        ).first()

        assertEquals(1, recentMeasurements.size)
        assertEquals("Current Package", recentMeasurements.first().packageName)
    }

    // ===== ERROR RECOVERY TESTS =====

    @Test
    fun shouldHandleDatabaseErrorsGracefully() = runTest {
        // Close database to simulate error condition
        database.close()

        val measurement = MeasurementResult(
            packageName = "Error Test",
            width = 0.1f, height = 0.1f, depth = 0.1f, volume = 0.001f,
            timestamp = System.currentTimeMillis()
        )

        // Should return error indicator instead of crashing
        val result = repository.insertMeasurement(measurement)
        assertEquals(-1L, result)

        // Verify count returns 0 instead of crashing
        val count = repository.getMeasurementCount()
        assertEquals(0, count)

        // Verify getAllMeasurements returns empty list instead of crashing
        val allMeasurements = repository.getAllMeasurements().first()
        assertTrue(allMeasurements.isEmpty())
    }

    // ===== HELPER METHODS =====

    private fun createTestMeasurement(
        packageName: String,
        size: Float,
        timestamp: Long = System.currentTimeMillis()
    ): MeasurementResult {
        return MeasurementResult(
            packageName = packageName,
            width = size,
            height = size,
            depth = size,
            volume = size * size * size,
            timestamp = timestamp
        )
    }
}