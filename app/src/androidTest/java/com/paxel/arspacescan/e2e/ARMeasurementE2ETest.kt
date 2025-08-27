package com.paxel.arspacescan.e2e

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.math.Vector3
import com.paxel.arspacescan.data.local.AppDatabase
import com.paxel.arspacescan.data.repository.MeasurementRepository
import com.paxel.arspacescan.data.model.MeasurementResult
import com.paxel.arspacescan.data.mapper.toPackageMeasurement
import com.paxel.arspacescan.data.mapper.toMeasurementResult
import com.paxel.arspacescan.ui.measurement.ARMeasurementViewModel
import com.paxel.arspacescan.ui.measurement.ARMeasurementActivity
import com.paxel.arspacescan.ui.measurement.MeasurementStep
import com.paxel.arspacescan.ui.result.MeasurementViewModel
import com.paxel.arspacescan.ui.result.MeasurementViewModelFactory
import com.paxel.arspacescan.util.PackageSizeValidator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue

/**
 * End-to-End workflow test untuk AR Measurement
 * Tests complete user journey dari AR measurement hingga database save
 */
@ExperimentalCoroutinesApi
@LargeTest
@RunWith(AndroidJUnit4::class)
class ARMeasurementE2ETest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private lateinit var database: AppDatabase
    private lateinit var repository: MeasurementRepository
    private lateinit var arViewModel: ARMeasurementViewModel
    private lateinit var measurementViewModel: MeasurementViewModel
    private lateinit var context: Context

    @Mock
    private lateinit var mockArActivity: ARMeasurementActivity

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()

        // Setup in-memory database
        database = Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        ).allowMainThreadQueries().build()

        repository = MeasurementRepository(database.measurementDao())

        // Initialize ViewModels
        arViewModel = ARMeasurementViewModel()
        measurementViewModel = MeasurementViewModel(repository)
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ===== COMPLETE USER WORKFLOW TESTS =====

    @Test
    fun shouldCompleteFullUserMeasurementWorkflowSuccessfully() = runTest {
        // PHASE 1: USER INPUT - Package Information
        val packageName = "iPhone 15 Pro Box"
        val declaredSize = "Medium"

        // PHASE 2: AR MEASUREMENT - Base Point Selection
        val baseCorners = createUnitCubeCorners()

        // Simulate user tapping 4 base points
        baseCorners.forEach { corner ->
            val anchorNode = createMockAnchorNode(corner)
            arViewModel.handleArTap(anchorNode, mockArActivity)
        }

        // Verify base is defined
        val baseDefinedState = arViewModel.uiState.first()
        assertEquals(MeasurementStep.BASE_DEFINED, baseDefinedState.step)
        assertEquals(4, baseDefinedState.corners.size)

        // PHASE 3: HEIGHT MEASUREMENT
        val heightPoint = Vector3(0f, 0.15f, 0f) // 15cm height
        val heightAnchorNode = createMockAnchorNode(heightPoint)
        arViewModel.handleArTap(heightAnchorNode, mockArActivity)

        // Verify measurement completed
        val completedState = arViewModel.uiState.first()
        assertEquals(MeasurementStep.COMPLETED, completedState.step)
        assertNotNull(completedState.finalResult)

        // PHASE 4: PRICE ESTIMATION
        val packageMeasurement = completedState.finalResult!!
        val measurementResult = packageMeasurement.copy(
            packageName = packageName,
            declaredSize = declaredSize
        ).toMeasurementResult()

        val validation = PackageSizeValidator.validate(context, measurementResult)
        val finalResult = measurementResult.copy(
            packageSizeCategory = validation.category,
            estimatedPrice = validation.estimatedPrice
        )

        // PHASE 5: DATABASE SAVE
        val savedId = saveMeasurement(finalResult)
        assertTrue("Measurement should be saved", savedId != -1L)

        // PHASE 6: VERIFICATION - Retrieve and Verify
        val retrievedResult = repository.getMeasurementByIdSync(savedId)
        assertNotNull("Retrieved result should not be null", retrievedResult)

        // Verify all data is preserved correctly
        assertEquals(packageName, retrievedResult!!.packageName)
        assertEquals(declaredSize, retrievedResult.declaredSize)
        assertTrue("Width should be positive", retrievedResult.width > 0)
        assertTrue("Height should be positive", retrievedResult.height > 0)
        assertTrue("Depth should be positive", retrievedResult.depth > 0)
        assertTrue("Volume should be positive", retrievedResult.volume > 0)
        assertTrue("Estimated price should be positive", retrievedResult.estimatedPrice > 0)
        assertTrue("Category should not be empty", retrievedResult.packageSizeCategory.isNotEmpty())
        assertTrue("Timestamp should be positive", retrievedResult.timestamp > 0)
    }

    @Test
    fun shouldHandleUserWorkflowWithPhotoDocumentation() = runTest {
        // Setup measurement
        val measurementResult = createTestMeasurement()
        val imagePath = "/storage/emulated/0/Pictures/test_image.jpg"

        val resultWithImage = measurementResult.copy(imagePath = imagePath)

        // Save with photo
        val savedId = saveMeasurement(resultWithImage)
        assertTrue("Measurement with photo should be saved", savedId != -1L)

        // Verify photo path saved
        val retrieved = repository.getMeasurementByIdSync(savedId)
        assertNotNull("Retrieved result should not be null", retrieved)
        assertEquals(imagePath, retrieved!!.imagePath)
    }

    @Test
    fun shouldHandleMultipleMeasurementSessionsCorrectly() = runTest {
        val sessions = listOf(
            TestSession("Small Box", 0.1f, "Sangat Kecil"),
            TestSession("Medium Box", 0.2f, "Kecil"),
            TestSession("Large Box", 0.3f, "Sedang")
        )

        val savedIds = mutableListOf<Long>()

        sessions.forEach { session ->
            // Simulate complete measurement session
            val measurementResult = createTestMeasurement(
                packageName = session.packageName,
                size = session.size
            )

            val validation = PackageSizeValidator.validate(context, measurementResult)
            val finalResult = measurementResult.copy(
                packageSizeCategory = validation.category,
                estimatedPrice = validation.estimatedPrice
            )

            val savedId = saveMeasurement(finalResult)
            assertTrue("Session measurement should be saved", savedId != -1L)
            savedIds.add(savedId)
        }

        // Verify all sessions saved
        assertEquals(3, savedIds.size)

        // Verify retrieval
        val allMeasurements = repository.getAllMeasurements().first()
        assertEquals(3, allMeasurements.size)

        // Verify each measurement
        sessions.forEachIndexed { index, session ->
            val retrieved = repository.getMeasurementByIdSync(savedIds[index])
            assertNotNull("Retrieved measurement should not be null", retrieved)
            assertEquals(session.packageName, retrieved!!.packageName)
            assertEquals(session.size, retrieved.width, 0.01f)
        }
    }

    // ===== ERROR RECOVERY WORKFLOW TESTS =====

    @Test
    fun shouldRecoverFromMeasurementErrorsGracefully() = runTest {
        // Simulate user making measurement error
        arViewModel.reset() // Start fresh

        // Add only 2 points instead of 4 (incomplete base)
        repeat(2) { index ->
            val corner = Vector3(index.toFloat(), 0f, 0f)
            val anchorNode = createMockAnchorNode(corner)
            arViewModel.handleArTap(anchorNode, mockArActivity)
        }

        val incompleteState = arViewModel.uiState.first()
        assertEquals(MeasurementStep.SELECT_BASE_POINT_3, incompleteState.step)
        assertEquals(2, incompleteState.corners.size)

        // User realizes mistake and uses undo
        arViewModel.undoLastPoint()

        val undoState = arViewModel.uiState.first()
        assertEquals(MeasurementStep.SELECT_BASE_POINT_2, undoState.step)
        assertEquals(1, undoState.corners.size)

        // User resets and starts over
        arViewModel.reset()

        val resetState = arViewModel.uiState.first()
        assertEquals(MeasurementStep.SELECT_BASE_POINT_1, resetState.step)
        assertEquals(0, resetState.corners.size)

        // Now complete measurement correctly
        val baseCorners = createUnitCubeCorners()
        baseCorners.forEach { corner ->
            val anchorNode = createMockAnchorNode(corner)
            arViewModel.handleArTap(anchorNode, mockArActivity)
        }

        // Add height
        val heightPoint = Vector3(0f, 0.1f, 0f)
        val heightAnchorNode = createMockAnchorNode(heightPoint)
        arViewModel.handleArTap(heightAnchorNode, mockArActivity)

        // Should complete successfully
        val finalState = arViewModel.uiState.first()
        assertEquals(MeasurementStep.COMPLETED, finalState.step)
        assertNotNull(finalState.finalResult)
    }

    @Test
    fun shouldHandleSaveFailuresGracefully() = runTest {
        // Create invalid measurement (will fail validation)
        val invalidMeasurement = MeasurementResult(
            packageName = "",
            width = -1f, // Invalid negative width
            height = 0.1f,
            depth = 0.1f,
            volume = -0.01f, // Invalid negative volume
            timestamp = System.currentTimeMillis()
        )

        // Attempt to save - should fail gracefully
        val result = repository.insertMeasurement(invalidMeasurement)
        assertEquals("Invalid measurement should fail to save", -1L, result)

        // Database should remain consistent
        val count = repository.getMeasurementCount()
        assertEquals(0, count)
    }

    // ===== PERFORMANCE WORKFLOW TESTS =====

    @Test
    fun shouldHandleRapidMeasurementOperationsEfficiently() = runTest {
        val startTime = System.currentTimeMillis()

        // Simulate rapid measurement creation (user doing multiple measurements quickly)
        repeat(20) { index ->
            // Create AR measurement
            arViewModel.reset()

            val baseCorners = createVariableCubeCorners(size = 0.1f + index * 0.01f)
            baseCorners.forEach { corner ->
                val anchorNode = createMockAnchorNode(corner)
                arViewModel.handleArTap(anchorNode, mockArActivity)
            }

            val heightPoint = Vector3(0f, 0.1f + index * 0.01f, 0f)
            val heightAnchorNode = createMockAnchorNode(heightPoint)
            arViewModel.handleArTap(heightAnchorNode, mockArActivity)

            val state = arViewModel.uiState.first()
            val packageMeasurement = state.finalResult!!

            // Convert and save
            val measurementResult = packageMeasurement.copy(
                packageName = "Rapid Test $index"
            ).toMeasurementResult()

            val validation = PackageSizeValidator.validate(context, measurementResult)
            val finalResult = measurementResult.copy(
                packageSizeCategory = validation.category,
                estimatedPrice = validation.estimatedPrice
            )

            saveMeasurement(finalResult)
        }

        val endTime = System.currentTimeMillis()
        val totalTime = endTime - startTime

        // Verify all measurements saved
        val finalCount = repository.getMeasurementCount()
        assertEquals(20, finalCount)

        // Performance assertion (adjust based on device capabilities)
        assertTrue("20 rapid measurements should complete within 10 seconds, took ${totalTime}ms",
            totalTime < 10000)
    }

    // ===== DATA CONSISTENCY WORKFLOW TESTS =====

    @Test
    fun shouldMaintainDataConsistencyAcrossConversions() = runTest {
        // Create measurement through AR flow
        val originalSize = 0.25f
        val originalVolume = originalSize * originalSize * originalSize

        val measurementResult = createTestMeasurement(
            packageName = "Consistency Test",
            size = originalSize
        )

        // Convert to PackageMeasurement
        val packageMeasurement = measurementResult.toPackageMeasurement()

        // Save to database
        val savedId = repository.insert(packageMeasurement)
        assertTrue("Package measurement should be saved", savedId > 0)

        // Retrieve as PackageMeasurement
        val retrievedPackage = database.measurementDao().getMeasurementByIdSync(savedId)
        assertNotNull("Retrieved package should not be null", retrievedPackage)

        // Convert back to MeasurementResult
        val convertedResult = retrievedPackage!!.toMeasurementResult()

        // Verify data consistency throughout conversions
        assertEquals(measurementResult.packageName, convertedResult.packageName)
        assertEquals(measurementResult.width, convertedResult.width, 0.001f)
        assertEquals(measurementResult.height, convertedResult.height, 0.001f)
        assertEquals(measurementResult.depth, convertedResult.depth, 0.001f)
        assertEquals(measurementResult.volume, convertedResult.volume, 0.0001f)
        assertEquals(measurementResult.packageSizeCategory, convertedResult.packageSizeCategory)
        assertEquals(measurementResult.estimatedPrice, convertedResult.estimatedPrice)
    }

    // ===== HELPER METHODS =====

    private fun createUnitCubeCorners(): List<Vector3> {
        return listOf(
            Vector3(0f, 0f, 0f),
            Vector3(0.1f, 0f, 0f),
            Vector3(0.1f, 0f, 0.1f),
            Vector3(0f, 0f, 0.1f)
        )
    }

    private fun createVariableCubeCorners(size: Float): List<Vector3> {
        return listOf(
            Vector3(0f, 0f, 0f),
            Vector3(size, 0f, 0f),
            Vector3(size, 0f, size),
            Vector3(0f, 0f, size)
        )
    }

    private fun createMockAnchorNode(position: Vector3): AnchorNode {
        val anchorNode = mock(AnchorNode::class.java)
        `when`(anchorNode.worldPosition).thenReturn(position)
        return anchorNode
    }

    private fun createTestMeasurement(
        packageName: String = "Test Package",
        declaredSize: String = "Medium",
        size: Float = 0.2f
    ): MeasurementResult {
        return MeasurementResult(
            packageName = packageName,
            declaredSize = declaredSize,
            width = size,
            height = size,
            depth = size,
            volume = size * size * size,
            timestamp = System.currentTimeMillis()
        )
    }

    private suspend fun saveMeasurement(measurementResult: MeasurementResult): Long {
        return repository.insertMeasurement(measurementResult)
    }

    // Helper data class for test sessions
    private data class TestSession(
        val packageName: String,
        val size: Float,
        val expectedCategory: String
    )
}