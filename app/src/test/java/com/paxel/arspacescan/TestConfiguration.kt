package com.paxel.arspacescan.test

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.math.Vector3
import com.paxel.arspacescan.data.local.AppDatabase
import com.paxel.arspacescan.data.model.MeasurementResult
import com.paxel.arspacescan.data.model.PackageMeasurement
import com.paxel.arspacescan.data.mapper.toPackageMeasurement
import com.paxel.arspacescan.data.mapper.toMeasurementResult
import com.paxel.arspacescan.data.repository.MeasurementRepository
import kotlinx.coroutines.runBlocking
import org.mockito.Mockito.*

/**
 * ✅ FINAL FIXED VERSION - Test configuration dan utilities untuk AR Measurement tests
 * Provides common setup, mock objects, dan helper functions
 *
 * FIXES APPLIED:
 * - ✅ Proper imports for ApplicationProvider
 * - ✅ Added PackageMeasurement generators for database operations
 * - ✅ Fixed repository method names and parameter types
 * - ✅ Added proper type conversion utilities
 * - ✅ Enhanced validation and assertion helpers
 */
object TestConfiguration {

    // ===== DATABASE SETUP =====

    /**
     * Create in-memory database untuk testing
     */
    fun createTestDatabase(context: Context = ApplicationProvider.getApplicationContext()): AppDatabase {
        return Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    /**
     * Setup repository dengan test database
     */
    fun createTestRepository(context: Context = ApplicationProvider.getApplicationContext()): MeasurementRepository {
        val database = createTestDatabase(context)
        return MeasurementRepository(database.measurementDao())
    }

    // ===== MOCK OBJECTS =====

    /**
     * Create mock Vector3 with specified coordinates
     */
    fun createMockVector3(x: Float, y: Float, z: Float): Vector3 {
        return Vector3(x, y, z)
    }

    /**
     * Create mock AnchorNode dengan world position
     */
    fun createMockAnchorNode(position: Vector3): AnchorNode {
        val anchorNode = mock(AnchorNode::class.java)
        `when`(anchorNode.worldPosition).thenReturn(position)
        return anchorNode
    }

    // ===== TEST DATA GENERATORS =====

    /**
     * Generate standard test measurement result (for UI/logic testing)
     */
    fun createTestMeasurementResult(
        id: Long = 0,
        packageName: String = "Test Package",
        declaredSize: String = "Medium",
        width: Float = 0.2f,
        height: Float = 0.15f,
        depth: Float = 0.1f,
        imagePath: String? = null,
        timestamp: Long = System.currentTimeMillis()
    ): MeasurementResult {
        return MeasurementResult(
            id = id,
            packageName = packageName,
            declaredSize = declaredSize,
            width = width,
            height = height,
            depth = depth,
            volume = width * height * depth,
            timestamp = timestamp,
            imagePath = imagePath,
            packageSizeCategory = "Tidak Diketahui",
            estimatedPrice = 0
        )
    }

    /**
     * ✅ ADDED: Generate PackageMeasurement for database operations
     */
    fun createTestPackageMeasurement(
        id: Long = 0,
        packageName: String = "Test Package",
        declaredSize: String = "Medium",
        width: Float = 0.2f,
        height: Float = 0.15f,
        depth: Float = 0.1f,
        imagePath: String? = null,
        timestamp: Long = System.currentTimeMillis()
    ): PackageMeasurement {
        return PackageMeasurement(
            id = id,
            packageName = packageName,
            declaredSize = declaredSize,
            width = width,
            height = height,
            depth = depth,
            volume = width * height * depth,
            timestamp = timestamp,
            imagePath = imagePath,
            packageSizeCategory = "Tidak Diketahui",
            estimatedPrice = 0
        )
    }

    /**
     * Generate unit cube corners (10cm x 10cm base)
     */
    fun createUnitCubeCorners(): List<Vector3> {
        return listOf(
            Vector3(0f, 0f, 0f),
            Vector3(0.1f, 0f, 0f),
            Vector3(0.1f, 0f, 0.1f),
            Vector3(0f, 0f, 0.1f)
        )
    }

    /**
     * Generate variable size cube corners
     */
    fun createCubeCorners(size: Float): List<Vector3> {
        return listOf(
            Vector3(0f, 0f, 0f),
            Vector3(size, 0f, 0f),
            Vector3(size, 0f, size),
            Vector3(0f, 0f, size)
        )
    }

    /**
     * Generate rectangular box corners
     */
    fun createRectangularCorners(width: Float, depth: Float): List<Vector3> {
        return listOf(
            Vector3(0f, 0f, 0f),
            Vector3(width, 0f, 0f),
            Vector3(width, 0f, depth),
            Vector3(0f, 0f, depth)
        )
    }

    /**
     * Generate irregular quadrilateral corners (for testing error cases)
     */
    fun createIrregularCorners(): List<Vector3> {
        return listOf(
            Vector3(0f, 0f, 0f),
            Vector3(0.15f, 0f, 0.05f),
            Vector3(0.12f, 0f, 0.18f),
            Vector3(-0.03f, 0f, 0.13f)
        )
    }

    // ===== BATCH TEST DATA =====

    /**
     * Generate multiple test measurements untuk bulk operations (UI/logic testing)
     */
    fun createBatchTestMeasurements(count: Int): List<MeasurementResult> {
        return (1..count).map { index ->
            createTestMeasurementResult(
                packageName = "Package $index",
                width = 0.1f + index * 0.01f,
                height = 0.1f + index * 0.01f,
                depth = 0.1f + index * 0.01f,
                timestamp = System.currentTimeMillis() + index * 1000L
            )
        }
    }

    /**
     * ✅ ADDED: Generate PackageMeasurement batch for repository operations
     */
    fun createBatchTestPackageMeasurements(count: Int): List<PackageMeasurement> {
        return (1..count).map { index ->
            createTestPackageMeasurement(
                packageName = "Package $index",
                width = 0.1f + index * 0.01f,
                height = 0.1f + index * 0.01f,
                depth = 0.1f + index * 0.01f,
                timestamp = System.currentTimeMillis() + index * 1000L
            )
        }
    }

    /**
     * Generate test measurements with different categories
     */
    fun createCategorizedTestMeasurements(): List<MeasurementResult> {
        return listOf(
            // Very Small
            createTestMeasurementResult(
                packageName = "Tiny Package",
                width = 0.05f, height = 0.05f, depth = 0.04f
            ),
            // Small
            createTestMeasurementResult(
                packageName = "Small Package",
                width = 0.15f, height = 0.10f, depth = 0.08f
            ),
            // Medium
            createTestMeasurementResult(
                packageName = "Medium Package",
                width = 0.25f, height = 0.20f, depth = 0.15f
            ),
            // Large
            createTestMeasurementResult(
                packageName = "Large Package",
                width = 0.40f, height = 0.30f, depth = 0.25f
            )
        )
    }

    // ===== VALIDATION HELPERS =====

    /**
     * Validate measurement dimensions are reasonable
     */
    fun isValidMeasurement(result: MeasurementResult): Boolean {
        return result.width > 0 && result.height > 0 && result.depth > 0 &&
                result.volume > 0 && result.timestamp > 0 &&
                result.width < 10f && result.height < 10f && result.depth < 10f
    }

    /**
     * Compare two measurements with tolerance
     */
    fun compareMeasurementsWithTolerance(
        expected: MeasurementResult,
        actual: MeasurementResult,
        tolerance: Float = 0.01f
    ): Boolean {
        return kotlin.math.abs(expected.width - actual.width) <= tolerance &&
                kotlin.math.abs(expected.height - actual.height) <= tolerance &&
                kotlin.math.abs(expected.depth - actual.depth) <= tolerance &&
                kotlin.math.abs(expected.volume - actual.volume) <= tolerance * 3 // Volume has cubic tolerance
    }

    // ===== PERFORMANCE MEASUREMENT =====

    /**
     * Measure execution time of a block
     */
    inline fun <T> measureTime(block: () -> T): Pair<T, Long> {
        val startTime = System.currentTimeMillis()
        val result = block()
        val endTime = System.currentTimeMillis()
        return Pair(result, endTime - startTime)
    }

    /**
     * Measure execution time of a suspend block
     */
    suspend inline fun <T> measureTimeSuspend(crossinline block: suspend () -> T): Pair<T, Long> {
        val startTime = System.currentTimeMillis()
        val result = block()
        val endTime = System.currentTimeMillis()
        return Pair(result, endTime - startTime)
    }

    // ===== ERROR CASE GENERATORS =====

    /**
     * Generate invalid measurement data untuk error testing
     */
    fun createInvalidMeasurements(): List<MeasurementResult> {
        return listOf(
            // Negative dimensions
            createTestMeasurementResult(width = -0.1f, height = 0.1f, depth = 0.1f),
            // Zero dimensions
            createTestMeasurementResult(width = 0f, height = 0.1f, depth = 0.1f),
            // Too large dimensions
            createTestMeasurementResult(width = 20f, height = 0.1f, depth = 0.1f),
            // Negative volume
            MeasurementResult(
                packageName = "Invalid Volume",
                width = 0.1f, height = 0.1f, depth = 0.1f,
                volume = -0.001f, // Manually set negative volume
                timestamp = System.currentTimeMillis()
            )
        )
    }

    /**
     * Generate degenerate corner cases
     */
    fun createDegenerateCases(): List<List<Vector3>> {
        return listOf(
            // All corners at same point
            List(4) { Vector3(0f, 0f, 0f) },
            // Collinear points
            listOf(
                Vector3(0f, 0f, 0f),
                Vector3(0.1f, 0f, 0f),
                Vector3(0.2f, 0f, 0f),
                Vector3(0.3f, 0f, 0f)
            ),
            // Very small area
            listOf(
                Vector3(0f, 0f, 0f),
                Vector3(0.001f, 0f, 0f),
                Vector3(0.001f, 0f, 0.001f),
                Vector3(0f, 0f, 0.001f)
            )
        )
    }

    // ===== DATABASE HELPERS =====

    /**
     * ✅ FIXED: Use correct repository method and type
     * Populate database dengan test data
     */
    suspend fun populateTestDatabase(repository: MeasurementRepository, count: Int = 10) {
        val measurements = createBatchTestPackageMeasurements(count)
        measurements.forEach { measurement ->
            repository.insert(measurement) // ✅ FIXED: Use correct method name
        }
    }

    /**
     * Clear database untuk clean test state
     */
    suspend fun clearTestDatabase(repository: MeasurementRepository) {
        repository.deleteAllMeasurements()
    }

    // ===== ASSERTION HELPERS =====

    /**
     * Assert measurement is approximately equal
     */
    fun assertMeasurementEquals(
        expected: MeasurementResult,
        actual: MeasurementResult,
        tolerance: Float = 0.01f,
        message: String = "Measurements should be equal"
    ) {
        if (!compareMeasurementsWithTolerance(expected, actual, tolerance)) {
            throw AssertionError("$message. Expected: $expected, Actual: $actual")
        }
    }

    /**
     * Assert volume calculation is correct
     */
    fun assertVolumeCalculation(
        width: Float, height: Float, depth: Float,
        actualVolume: Float,
        tolerance: Float = 0.001f
    ) {
        val expectedVolume = width * height * depth
        val difference = kotlin.math.abs(expectedVolume - actualVolume)
        if (difference > tolerance) {
            throw AssertionError(
                "Volume calculation incorrect. Expected: $expectedVolume, " +
                        "Actual: $actualVolume, Difference: $difference"
            )
        }
    }

    // ===== AR TESTING UTILITIES =====

    /**
     * ✅ ADDED: AR-specific testing utilities
     */
    object ARTestingUtils {
        fun createMockArSession(): com.google.ar.core.Session? {
            // Return mock session for testing - implementation depends on mocking framework
            return mock(com.google.ar.core.Session::class.java)
        }

        fun simulateArTap(x: Float, y: Float): List<Vector3> {
            // Simulate AR tap and return hit points
            return listOf(Vector3(x, 0f, y))
        }

        fun createMockPlane(
            trackingState: com.google.ar.core.TrackingState = com.google.ar.core.TrackingState.TRACKING
        ): com.google.ar.core.Plane {
            val plane = mock(com.google.ar.core.Plane::class.java)
            `when`(plane.trackingState).thenReturn(trackingState)
            return plane
        }
    }

    // ===== PERFORMANCE BENCHMARKS =====

    /**
     * ✅ ADDED: Performance benchmark utilities
     */
    object PerformanceBenchmarks {
        suspend fun benchmarkMeasurementCalculation(corners: List<Vector3>, height: Float): Long {
            val (_, duration) = measureTimeSuspend {
                // Simulate measurement calculation
                corners.forEach { corner ->
                    // Processing time
                    kotlinx.coroutines.delay(1)
                }
            }
            return duration
        }

        fun benchmarkDatabaseOperations(repository: MeasurementRepository, count: Int): Long {
            val (_, duration) = measureTime {
                runBlocking {
                    val measurements = createBatchTestPackageMeasurements(count)
                    measurements.forEach { measurement ->
                        repository.insert(measurement)
                    }
                }
            }
            return duration
        }
    }
}

/**
 * ✅ FIXED: Extension functions untuk easier test setup
 */

// Repository extensions with correct method names and types
suspend fun MeasurementRepository.insertTestMeasurement(
    packageName: String = "Test Package",
    size: Float = 0.1f
): Long {
    val measurement = TestConfiguration.createTestPackageMeasurement(
        packageName = packageName,
        width = size,
        height = size,
        depth = size
    )
    return this.insert(measurement) // ✅ FIXED: Use correct method name
}

// Context extensions
fun Context.createTestRepository(): MeasurementRepository {
    return TestConfiguration.createTestRepository(this)
}

// ✅ FIXED: List extensions untuk batch operations with correct types
fun List<PackageMeasurement>.insertAllToRepository(repository: MeasurementRepository) = runBlocking {
    this@insertAllToRepository.forEach { measurement ->
        repository.insert(measurement) // ✅ FIXED: Use correct method name
    }
}

// ✅ ADDED: Conversion extensions for testing
fun List<MeasurementResult>.toPackageMeasurements(): List<PackageMeasurement> {
    return this.map { it.toPackageMeasurement() }
}

fun List<PackageMeasurement>.toMeasurementResults(): List<MeasurementResult> {
    return this.map { it.toMeasurementResult() }
}