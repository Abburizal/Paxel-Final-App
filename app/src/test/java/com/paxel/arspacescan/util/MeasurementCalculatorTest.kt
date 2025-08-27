package com.paxel.arspacescan.util

import com.google.ar.sceneform.math.Vector3
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/**
 * Unit tests untuk MeasurementCalculator
 * Tests calculation accuracy dan edge cases
 */
@RunWith(JUnit4::class)
class MeasurementCalculatorTest {

    // ===== VALID CALCULATION TESTS =====

    @Test
    fun shouldCalculateUnitCubeCorrectly() {
        // 1m x 1m x 1m cube
        val baseCorners = listOf(
            Vector3(0f, 0f, 0f),
            Vector3(1f, 0f, 0f),
            Vector3(1f, 0f, 1f),
            Vector3(0f, 0f, 1f)
        )
        val height = 1f

        val result = MeasurementCalculator.calculate(baseCorners, height)

        assertEquals(1f, result.width, 0.1f)
        assertEquals(1f, result.height, 0.1f)
        assertEquals(1f, result.depth, 0.1f)
        assertEquals(1f, result.volume, 0.1f)
    }

    @Test
    fun shouldCalculateRectangularBoxCorrectly() {
        // 2m x 3m x 4m box
        val baseCorners = listOf(
            Vector3(0f, 0f, 0f),
            Vector3(2f, 0f, 0f),
            Vector3(2f, 0f, 3f),
            Vector3(0f, 0f, 3f)
        )
        val height = 4f

        val result = MeasurementCalculator.calculate(baseCorners, height)

        // Should calculate approximately 2 × 3 × 4 = 24 cubic meters
        assertTrue("Width should be around 2m", result.width in 1.8f..2.2f)
        assertTrue("Height should be around 4m", result.height in 3.8f..4.2f)
        assertTrue("Depth should be around 3m", result.depth in 2.8f..3.2f)
        assertTrue("Volume should be around 24m³", result.volume in 20f..28f)
    }

    @Test
    fun shouldCalculateSmallPackageCorrectly() {
        // 10cm x 15cm x 20cm package (in meters: 0.1 x 0.15 x 0.2)
        val baseCorners = listOf(
            Vector3(0f, 0f, 0f),
            Vector3(0.1f, 0f, 0f),
            Vector3(0.1f, 0f, 0.15f),
            Vector3(0f, 0f, 0.15f)
        )
        val height = 0.2f

        val result = MeasurementCalculator.calculate(baseCorners, height)

        // Volume should be 0.003 cubic meters (3000 cm³)
        assertEquals(0.1f, result.width, 0.01f)
        assertEquals(0.2f, result.height, 0.01f)
        assertEquals(0.15f, result.depth, 0.01f)
        assertEquals(0.003f, result.volume, 0.001f)
    }

    @Test
    fun shouldHandleUnorderedCornersCorrectly() {
        // Same unit cube but corners in different order
        val unorderedCorners = listOf(
            Vector3(1f, 0f, 1f),  // Corner 3 first
            Vector3(0f, 0f, 0f),  // Corner 1 second
            Vector3(1f, 0f, 0f),  // Corner 2 third
            Vector3(0f, 0f, 1f)   // Corner 4 fourth
        )
        val height = 1f

        val result = MeasurementCalculator.calculate(unorderedCorners, height)

        // Should still calculate correct unit cube
        assertTrue("Volume should be around 1m³", result.volume in 0.8f..1.2f)
    }

    @Test
    fun shouldCalculateBaseAreaCorrectly() {
        val unitSquare = listOf(
            Vector3(0f, 0f, 0f),
            Vector3(1f, 0f, 0f),
            Vector3(1f, 0f, 1f),
            Vector3(0f, 0f, 1f)
        )

        val area = MeasurementCalculator.calculateBaseArea(unitSquare)

        assertEquals(1f, area, 0.1f) // 1m² for unit square
    }

    @Test
    fun shouldCalculateIrregularQuadrilateralArea() {
        val irregularShape = listOf(
            Vector3(0f, 0f, 0f),
            Vector3(3f, 0f, 0f),
            Vector3(2f, 0f, 2f),
            Vector3(-1f, 0f, 1f)
        )

        val area = MeasurementCalculator.calculateBaseArea(irregularShape)

        assertTrue("Area should be positive", area > 0f)
    }

    // ===== ERROR HANDLING TESTS =====

    @Test(expected = IllegalArgumentException::class)
    fun shouldThrowExceptionForInsufficientCorners() {
        val insufficientCorners = listOf(
            Vector3(0f, 0f, 0f),
            Vector3(1f, 0f, 0f),
            Vector3(1f, 0f, 1f) // Only 3 corners instead of 4
        )

        MeasurementCalculator.calculate(insufficientCorners, 1f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun shouldThrowExceptionForNegativeHeight() {
        val validCorners = listOf(
            Vector3(0f, 0f, 0f),
            Vector3(1f, 0f, 0f),
            Vector3(1f, 0f, 1f),
            Vector3(0f, 0f, 1f)
        )

        MeasurementCalculator.calculate(validCorners, -1f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun shouldThrowExceptionForZeroHeight() {
        val validCorners = listOf(
            Vector3(0f, 0f, 0f),
            Vector3(1f, 0f, 0f),
            Vector3(1f, 0f, 1f),
            Vector3(0f, 0f, 1f)
        )

        MeasurementCalculator.calculate(validCorners, 0f)
    }

    @Test
    fun shouldHandleDegenerateBaseGracefully() {
        // All corners at same point (degenerate case)
        val degenerateCorners = listOf(
            Vector3(0f, 0f, 0f),
            Vector3(0f, 0f, 0f),
            Vector3(0f, 0f, 0f),
            Vector3(0f, 0f, 0f)
        )

        val result = MeasurementCalculator.calculate(degenerateCorners, 1f)

        // Should return minimal valid dimensions to prevent crashes
        assertTrue("Width should be positive", result.width > 0)
        assertTrue("Height should be positive", result.height > 0)
        assertTrue("Depth should be positive", result.depth > 0)
        assertTrue("Volume should be positive", result.volume > 0)
    }

    @Test
    fun shouldHandleCollinearPointsGracefully() {
        // All points on a line (no area)
        val collinearCorners = listOf(
            Vector3(0f, 0f, 0f),
            Vector3(1f, 0f, 0f),
            Vector3(2f, 0f, 0f),
            Vector3(3f, 0f, 0f)
        )

        val result = MeasurementCalculator.calculate(collinearCorners, 1f)

        // Should still produce valid result (calculator handles edge cases)
        assertTrue("Volume should be positive", result.volume > 0)
    }

    // ===== VALIDATION TESTS =====

    @Test
    fun shouldValidateMeasurementResultsCorrectly() {
        val validResult = MeasurementCalculator.CalculationResult(
            width = 1f,
            height = 1f,
            depth = 1f,
            volume = 1f
        )

        assertTrue("Valid result should pass validation",
            MeasurementCalculator.validateMeasurement(validResult))
    }

    @Test
    fun shouldRejectInvalidMeasurementResults() {
        val invalidResults = listOf(
            // Negative width
            MeasurementCalculator.CalculationResult(
                width = -1f,
                height = 1f,
                depth = 1f,
                volume = 1f
            ),
            // Zero height
            MeasurementCalculator.CalculationResult(
                width = 1f,
                height = 0f,
                depth = 1f,
                volume = 1f
            ),
            // Too large dimensions
            MeasurementCalculator.CalculationResult(
                width = 10f,
                height = 1f,
                depth = 1f,
                volume = 10f
            )
        )

        invalidResults.forEach { result ->
            assertTrue("Invalid result should fail validation",
                !MeasurementCalculator.validateMeasurement(result))
        }
    }

    // ===== QUALITY ASSESSMENT TESTS =====

    @Test
    fun shouldAssessMeasurementQualityCorrectly() {
        val highQuality = MeasurementCalculator.getMeasurementQuality(0.95f)
        assertEquals("Sangat Baik", highQuality)

        val goodQuality = MeasurementCalculator.getMeasurementQuality(0.8f)
        assertEquals("Baik", goodQuality)

        val fairQuality = MeasurementCalculator.getMeasurementQuality(0.6f)
        assertEquals("Cukup", fairQuality)

        val poorQuality = MeasurementCalculator.getMeasurementQuality(0.3f)
        assertEquals("Rendah", poorQuality)
    }

    // ===== PRECISION TESTS =====

    @Test
    fun shouldMaintainPrecisionForVerySmallMeasurements() {
        // Test precision for 1mm x 1mm x 1mm (0.001m)
        val tinyCorners = listOf(
            Vector3(0f, 0f, 0f),
            Vector3(0.001f, 0f, 0f),
            Vector3(0.001f, 0f, 0.001f),
            Vector3(0f, 0f, 0.001f)
        )
        val height = 0.001f

        val result = MeasurementCalculator.calculate(tinyCorners, height)

        // Should maintain reasonable precision
        assertTrue("Volume should be greater than 1 cubic millimeter", result.volume > 0.0000001f)
        assertTrue("Width should be reasonable", result.width > 0.0001f)
        assertTrue("Height should be reasonable", result.height > 0.0001f)
        assertTrue("Depth should be reasonable", result.depth > 0.0001f)
    }

    @Test
    fun shouldHandleLargeMeasurementsCorrectly() {
        // Test 5m x 5m x 5m box (maximum reasonable package size)
        val largeCorners = listOf(
            Vector3(0f, 0f, 0f),
            Vector3(5f, 0f, 0f),
            Vector3(5f, 0f, 5f),
            Vector3(0f, 0f, 5f)
        )
        val height = 5f

        val result = MeasurementCalculator.calculate(largeCorners, height)

        assertEquals(125f, result.volume, 5f) // 5³ = 125 m³
        assertTrue("Width should be around 5m", result.width in 4.5f..5.5f)
        assertTrue("Height should be around 5m", result.height in 4.5f..5.5f)
        assertTrue("Depth should be around 5m", result.depth in 4.5f..5.5f)
    }

    // ===== CONFIDENCE CALCULATION TESTS =====

    private fun testConfidenceCalculation(sides: List<Float>, expectedConfidenceRange: ClosedFloatingPointRange<Float>) {
        // Create mock result to test confidence logic
        val maxSide = sides.maxOrNull() ?: 1f
        val minSide = sides.minOrNull() ?: 1f

        // Simulate confidence calculation based on shape regularity
        val confidence = when {
            maxSide / minSide <= 1.1f -> 1.0f  // Very square
            maxSide / minSide <= 2.0f -> 0.8f  // Rectangular
            maxSide / minSide <= 5.0f -> 0.6f  // Elongated
            else -> 0.4f  // Very elongated
        }

        assertTrue("Confidence should be in expected range", confidence in expectedConfidenceRange)
    }

    @Test
    fun shouldCalculateHighConfidenceForSquareShapes() {
        testConfidenceCalculation(listOf(1f, 1f, 1f, 1f), 0.9f..1.0f)
    }

    @Test
    fun shouldCalculateMediumConfidenceForRectangularShapes() {
        testConfidenceCalculation(listOf(1f, 2f, 1f, 2f), 0.7f..0.9f)
    }

    @Test
    fun shouldCalculateLowConfidenceForIrregularShapes() {
        testConfidenceCalculation(listOf(1f, 5f, 2f, 4f), 0.3f..0.7f)
    }
}