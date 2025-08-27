package com.paxel.arspacescan.ui.measurement

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.math.Vector3
import com.paxel.arspacescan.R
import com.paxel.arspacescan.test.TestConfiguration
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import org.junit.*
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.MockitoJUnitRunner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue

/**
 * Unit tests untuk AR Measurement State Flow
 * Tests comprehensive state management dan calculation flow
 * ✅ FINAL FIXED VERSION - All imports and coroutines properly configured
 */
@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class ARMeasurementViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    // ✅ FIXED: Proper test dispatcher configuration
    private val testDispatcher = UnconfinedTestDispatcher()

    @Mock
    private lateinit var mockArMeasurementActivity: ARMeasurementActivity

    private lateinit var viewModel: ARMeasurementViewModel

    @Before
    fun setup() {
        // ✅ FIXED: Set main dispatcher for testing
        Dispatchers.setMain(testDispatcher)
        viewModel = ARMeasurementViewModel()
    }

    @After
    fun tearDown() {
        // ✅ FIXED: Reset main dispatcher
        Dispatchers.resetMain()
    }

    // ===== INITIAL STATE TESTS =====

    @Test
    fun initialStateShouldBeCorrect() = runTest {
        val initialState = viewModel.uiState.first()

        assertEquals(MeasurementStep.SELECT_BASE_POINT_1, initialState.step)
        assertEquals(R.string.instruction_step_1, initialState.instructionTextId)
        assertTrue(initialState.corners.isEmpty())
        assertEquals(null, initialState.finalResult)
        assertFalse(initialState.isUndoEnabled)
    }

    // ===== STATE TRANSITION TESTS =====

    @Test
    fun shouldTransitionThroughBasePointSelectionCorrectly() = runTest {
        // Test Point 1 - Using TestConfiguration utilities
        val point1 = TestConfiguration.createMockVector3(0f, 0f, 0f)
        val anchorNode1 = TestConfiguration.createMockAnchorNode(point1)

        viewModel.handleArTap(anchorNode1, mockArMeasurementActivity)

        val state1 = viewModel.uiState.first()
        assertEquals(MeasurementStep.SELECT_BASE_POINT_2, state1.step)
        assertEquals(1, state1.corners.size)
        assertTrue(state1.isUndoEnabled)

        // Test Point 2
        val point2 = TestConfiguration.createMockVector3(1f, 0f, 0f)
        val anchorNode2 = TestConfiguration.createMockAnchorNode(point2)

        viewModel.handleArTap(anchorNode2, mockArMeasurementActivity)

        val state2 = viewModel.uiState.first()
        assertEquals(MeasurementStep.SELECT_BASE_POINT_3, state2.step)
        assertEquals(2, state2.corners.size)

        // Test Point 3
        val point3 = TestConfiguration.createMockVector3(1f, 0f, 1f)
        val anchorNode3 = TestConfiguration.createMockAnchorNode(point3)

        viewModel.handleArTap(anchorNode3, mockArMeasurementActivity)

        val state3 = viewModel.uiState.first()
        assertEquals(MeasurementStep.SELECT_BASE_POINT_4, state3.step)
        assertEquals(3, state3.corners.size)

        // Test Point 4 - Should transition to BASE_DEFINED
        val point4 = TestConfiguration.createMockVector3(0f, 0f, 1f)
        val anchorNode4 = TestConfiguration.createMockAnchorNode(point4)

        viewModel.handleArTap(anchorNode4, mockArMeasurementActivity)

        val state4 = viewModel.uiState.first()
        assertEquals(MeasurementStep.BASE_DEFINED, state4.step)
        assertEquals(4, state4.corners.size)
        assertEquals(R.string.instruction_set_height, state4.instructionTextId)
    }

    @Test
    fun shouldCompleteMeasurementWhenHeightIsConfirmed() = runTest {
        // Setup base points first using TestConfiguration
        setupBasePoints()

        // Confirm height (simulate height measurement)
        val heightPoint = TestConfiguration.createMockVector3(0f, 1f, 0f) // 1 meter height
        val heightAnchorNode = TestConfiguration.createMockAnchorNode(heightPoint)

        viewModel.handleArTap(heightAnchorNode, mockArMeasurementActivity)

        val finalState = viewModel.uiState.first()
        assertEquals(MeasurementStep.COMPLETED, finalState.step)
        assertEquals(R.string.instruction_measurement_complete, finalState.instructionTextId)
        assertNotNull(finalState.finalResult)
        assertFalse(finalState.isUndoEnabled)

        // Verify calculation results using TestConfiguration validation
        val result = finalState.finalResult!!
        assertTrue("Width should be positive", result.width > 0)
        assertTrue("Height should be positive", result.height > 0)
        assertTrue("Depth should be positive", result.depth > 0)
        assertTrue("Volume should be positive", result.volume > 0)

        // ✅ FIXED: Use TestConfiguration assertion helper
        TestConfiguration.assertVolumeCalculation(
            result.width, result.height, result.depth, result.volume
        )
    }

    // ===== UNDO FUNCTIONALITY TESTS =====

    @Test
    fun shouldUndoLastPointCorrectly() = runTest {
        // Add two points using TestConfiguration
        val point1 = TestConfiguration.createMockVector3(0f, 0f, 0f)
        val anchorNode1 = TestConfiguration.createMockAnchorNode(point1)
        viewModel.handleArTap(anchorNode1, mockArMeasurementActivity)

        val point2 = TestConfiguration.createMockVector3(1f, 0f, 0f)
        val anchorNode2 = TestConfiguration.createMockAnchorNode(point2)
        viewModel.handleArTap(anchorNode2, mockArMeasurementActivity)

        // Verify we're at point 3 step
        val beforeUndo = viewModel.uiState.first()
        assertEquals(MeasurementStep.SELECT_BASE_POINT_3, beforeUndo.step)
        assertEquals(2, beforeUndo.corners.size)

        // Undo last point
        viewModel.undoLastPoint()

        // Verify we're back to point 2 step
        val afterUndo = viewModel.uiState.first()
        assertEquals(MeasurementStep.SELECT_BASE_POINT_2, afterUndo.step)
        assertEquals(1, afterUndo.corners.size)
        assertTrue(afterUndo.isUndoEnabled)
    }

    @Test
    fun shouldHandleUndoFromEmptyStateGracefully() = runTest {
        // Initial state should have no corners
        val initialState = viewModel.uiState.first()
        assertEquals(0, initialState.corners.size)

        // Undo should not crash and state should remain unchanged
        viewModel.undoLastPoint()

        val afterUndo = viewModel.uiState.first()
        assertEquals(MeasurementStep.SELECT_BASE_POINT_1, afterUndo.step)
        assertEquals(0, afterUndo.corners.size)
        assertFalse(afterUndo.isUndoEnabled)
    }

    // ===== RESET FUNCTIONALITY TESTS =====

    @Test
    fun shouldResetToInitialStateCorrectly() = runTest {
        // Add some points and complete measurement
        setupCompleteValidMeasurement()

        // Reset
        viewModel.reset()

        // Verify back to initial state
        val resetState = viewModel.uiState.first()
        assertEquals(MeasurementStep.SELECT_BASE_POINT_1, resetState.step)
        assertEquals(R.string.instruction_step_1, resetState.instructionTextId)
        assertEquals(0, resetState.corners.size)
        assertEquals(null, resetState.finalResult)
        assertFalse(resetState.isUndoEnabled)
    }

    // ===== MEASUREMENT CALCULATION TESTS =====

    @Test
    fun shouldCalculateCorrectVolumeForUnitCube() = runTest {
        // ✅ ENHANCED: Use TestConfiguration pre-built corners
        val unitCubeCorners = TestConfiguration.createUnitCubeCorners()

        // Add unit cube base points
        unitCubeCorners.forEach { point ->
            val anchorNode = TestConfiguration.createMockAnchorNode(point)
            viewModel.handleArTap(anchorNode, mockArMeasurementActivity)
        }

        // Add 0.1m height (same as base size for perfect cube)
        val heightPoint = TestConfiguration.createMockVector3(0f, 0.1f, 0f)
        val heightAnchorNode = TestConfiguration.createMockAnchorNode(heightPoint)
        viewModel.handleArTap(heightAnchorNode, mockArMeasurementActivity)

        val result = viewModel.uiState.first().finalResult!!

        // Use TestConfiguration validation
        TestConfiguration.assertVolumeCalculation(
            result.width, result.height, result.depth, result.volume,
            tolerance = 0.01f // 1cm tolerance
        )
    }

    @Test
    fun shouldCalculateCorrectVolumeForRectangularBox() = runTest {
        // ✅ ENHANCED: Use TestConfiguration rectangular corners
        val corners = TestConfiguration.createRectangularCorners(2f, 3f) // 2m x 3m base

        // Add base corners
        corners.forEach { point ->
            val anchorNode = TestConfiguration.createMockAnchorNode(point)
            viewModel.handleArTap(anchorNode, mockArMeasurementActivity)
        }

        // Add 4m height
        val heightPoint = TestConfiguration.createMockVector3(0f, 4f, 0f)
        val heightAnchorNode = TestConfiguration.createMockAnchorNode(heightPoint)
        viewModel.handleArTap(heightAnchorNode, mockArMeasurementActivity)

        val result = viewModel.uiState.first().finalResult!!

        // Volume should be approximately 2 × 3 × 4 = 24 cubic meters
        TestConfiguration.assertVolumeCalculation(
            result.width, result.height, result.depth, result.volume,
            tolerance = 1f // 1m³ tolerance for larger measurements
        )
    }

    // ===== ERROR HANDLING TESTS =====

    @Test
    fun shouldHandleInvalidMeasurementsGracefully() = runTest {
        // Try to set height before base is defined
        val heightPoint = TestConfiguration.createMockVector3(0f, 1f, 0f)
        val heightAnchorNode = TestConfiguration.createMockAnchorNode(heightPoint)

        // Should not transition to completed
        viewModel.handleArTap(heightAnchorNode, mockArMeasurementActivity)

        val state = viewModel.uiState.first()
        assertEquals(MeasurementStep.SELECT_BASE_POINT_2, state.step) // Should advance base points
        assertEquals(null, state.finalResult)
    }

    @Test
    fun shouldValidateMeasurementResults() = runTest {
        // ✅ ENHANCED: Use TestConfiguration degenerate case
        val degenerateCorners = TestConfiguration.createDegenerateCases()[0] // All same point

        degenerateCorners.forEach { point ->
            val anchorNode = TestConfiguration.createMockAnchorNode(point)
            viewModel.handleArTap(anchorNode, mockArMeasurementActivity)
        }

        // Add height
        val heightPoint = TestConfiguration.createMockVector3(0f, 1f, 0f)
        val heightAnchorNode = TestConfiguration.createMockAnchorNode(heightPoint)
        viewModel.handleArTap(heightAnchorNode, mockArMeasurementActivity)

        val result = viewModel.uiState.first().finalResult

        // Should complete but with minimal valid dimensions
        assertNotNull(result)
        assertTrue("Volume should be positive even for degenerate case", result!!.volume > 0)

        // ✅ FIXED: Validate using TestConfiguration
        val measurementResult = result.let { pm ->
            TestConfiguration.createTestMeasurementResult(
                width = pm.width,
                height = pm.height,
                depth = pm.depth
            )
        }
        assertTrue("Should produce valid measurement", TestConfiguration.isValidMeasurement(measurementResult))
    }

    // ===== NAVIGATION EVENT TESTS =====

    @Test
    fun shouldEmitNavigationEventWhenMeasurementComplete() = runTest {
        // Setup complete measurement
        setupCompleteValidMeasurement()

        // Collect navigation events
        val navigationEvents = mutableListOf<Any>()

        // ✅ FIXED: Proper launch usage in test scope
        val job = launch {
            viewModel.navigationEvent.collect { event ->
                navigationEvents.add(event)
            }
        }

        // Trigger navigation
        viewModel.navigateToResult()

        // ✅ FIXED: Use proper delay
        delay(100)

        // Verify navigation event emitted
        assertTrue("Should emit navigation event", navigationEvents.isNotEmpty())
        assertNotNull("Navigation event should not be null", navigationEvents.first())

        job.cancel()
    }

    // ===== PERFORMANCE TESTS =====

    @Test
    fun shouldCompleteCalculationWithinReasonableTime() = runTest {
        // ✅ ADDED: Performance testing using TestConfiguration
        val (result, duration) = TestConfiguration.measureTime {
            runBlocking {
                setupCompleteValidMeasurement()
            }
        }

        // Should complete measurement setup within 1 second
        assertTrue("Measurement should complete quickly (was ${duration}ms)", duration < 1000)

        val finalState = viewModel.uiState.first()
        assertEquals(MeasurementStep.COMPLETED, finalState.step)
    }

    // ===== HELPER METHODS =====

    private suspend fun setupBasePoints() {
        val basePoints = TestConfiguration.createUnitCubeCorners()
        basePoints.forEach { point ->
            val anchorNode = TestConfiguration.createMockAnchorNode(point)
            viewModel.handleArTap(anchorNode, mockArMeasurementActivity)
        }
    }

    private suspend fun setupCompleteValidMeasurement() {
        setupBasePoints()

        // Complete with height
        val heightPoint = TestConfiguration.createMockVector3(0f, 0.1f, 0f)
        val heightAnchorNode = TestConfiguration.createMockAnchorNode(heightPoint)
        viewModel.handleArTap(heightAnchorNode, mockArMeasurementActivity)
    }
}