package com.paxel.arspacescan.util

import android.util.Log
import com.google.ar.sceneform.math.Vector3
import com.paxel.arspacescan.data.model.BoxResult
import kotlin.math.*

object MeasurementCalculator {

    private const val TAG = "MeasurementCalculator"

    data class CalculationResult(
        val width: Float,
        val height: Float,
        val depth: Float,
        val volume: Float,
        val confidence: Float = 1.0f
    )

    /**
     * Calculate box dimensions from 4 base corners and height
     * Enhanced version with proper corner ordering and validation
     */
    fun calculate(baseCorners: List<Vector3>, height: Float): BoxResult {
        if (baseCorners.size != 4) {
            throw IllegalArgumentException("Need exactly 4 base corners, got ${baseCorners.size}")
        }

        if (height <= 0) {
            throw IllegalArgumentException("Height must be positive, got $height")
        }

        return try {
            val orderedCorners = orderCorners(baseCorners)
            val dimensions = calculateDimensions(orderedCorners, height)

            BoxResult(
                width = dimensions.width,
                height = dimensions.height,
                depth = dimensions.depth,
                volume = dimensions.volume
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating dimensions", e)
            // Return minimal valid result rather than crashing
            BoxResult(0.01f, height, 0.01f, 0.01f * 0.01f * height)
        }
    }

    /**
     * Order corners to form a proper rectangle
     * Uses centroid-based sorting by angle
     */
    private fun orderCorners(corners: List<Vector3>): List<Vector3> {
        if (corners.size != 4) return corners

        try {
            // Calculate centroid
            val centroidX = corners.sumOf { it.x.toDouble() }.toFloat() / 4
            val centroidZ = corners.sumOf { it.z.toDouble() }.toFloat() / 4

            // Sort corners by angle from centroid (counter-clockwise)
            return corners.sortedBy { corner ->
                atan2(corner.z - centroidZ, corner.x - centroidX)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error ordering corners, using original order", e)
            return corners
        }
    }

    /**
     * Calculate dimensions from ordered corners
     */
    private fun calculateDimensions(corners: List<Vector3>, height: Float): CalculationResult {
        // Calculate all four side lengths
        val sides = listOf(
            distance(corners[0], corners[1]),
            distance(corners[1], corners[2]),
            distance(corners[2], corners[3]),
            distance(corners[3], corners[0])
        )

        // Group opposite sides and take average for more accuracy
        val width = (sides[0] + sides[2]) / 2f
        val depth = (sides[1] + sides[3]) / 2f

        // Ensure minimum dimensions
        val finalWidth = maxOf(width, 0.001f)
        val finalDepth = maxOf(depth, 0.001f)
        val finalHeight = maxOf(height, 0.001f)

        val volume = finalWidth * finalDepth * finalHeight

        // Calculate confidence based on how rectangular the shape is
        val confidence = calculateConfidence(sides)

        return CalculationResult(
            width = finalWidth,
            height = finalHeight,
            depth = finalDepth,
            volume = volume,
            confidence = confidence
        )
    }

    /**
     * Calculate confidence based on how rectangular the base is
     */
    private fun calculateConfidence(sides: List<Float>): Float {
        if (sides.size != 4) return 0.5f

        return try {
            // Check how similar opposite sides are
            val widthSimilarity = 1f - abs(sides[0] - sides[2]) / maxOf(sides[0], sides[2])
            val depthSimilarity = 1f - abs(sides[1] - sides[3]) / maxOf(sides[1], sides[3])

            ((widthSimilarity + depthSimilarity) / 2f).coerceIn(0.1f, 1.0f)
        } catch (e: Exception) {
            Log.w(TAG, "Error calculating confidence", e)
            0.5f
        }
    }

    /**
     * Calculate distance between two points (ignoring Y axis for base measurements)
     */
    private fun distance(p1: Vector3, p2: Vector3): Float {
        val dx = p1.x - p2.x
        val dz = p1.z - p2.z
        return sqrt(dx * dx + dz * dz)
    }

    /**
     * Validate measurement result
     */
    fun validateMeasurement(result: CalculationResult): Boolean {
        return result.width > 0.001f &&
                result.height > 0.001f &&
                result.depth > 0.001f &&
                result.width < 5.0f &&
                result.height < 5.0f &&
                result.depth < 5.0f &&
                result.volume > 0.000001f
    }

    /**
     * Calculate area of base from corners (for validation)
     */
    fun calculateBaseArea(corners: List<Vector3>): Float {
        if (corners.size != 4) return 0f

        return try {
            val orderedCorners = orderCorners(corners)

            // Using shoelace formula for polygon area
            var area = 0f
            for (i in orderedCorners.indices) {
                val j = (i + 1) % orderedCorners.size
                area += orderedCorners[i].x * orderedCorners[j].z
                area -= orderedCorners[j].x * orderedCorners[i].z
            }

            abs(area) / 2f
        } catch (e: Exception) {
            Log.w(TAG, "Error calculating base area", e)
            0f
        }
    }

    /**
     * Get measurement quality assessment
     */
    fun getMeasurementQuality(confidence: Float): String {
        return when {
            confidence >= 0.9f -> "Sangat Baik"
            confidence >= 0.7f -> "Baik"
            confidence >= 0.5f -> "Cukup"
            else -> "Rendah"
        }
    }

    /**
     * Validate input corners before calculation
     */
    fun validateCorners(corners: List<Vector3>): Boolean {
        if (corners.size != 4) return false

        return try {
            // Check if all corners are distinct
            for (i in corners.indices) {
                for (j in i + 1 until corners.size) {
                    val dist = distance(corners[i], corners[j])
                    if (dist < 0.01f) return false // Corners too close
                }
            }

            // Check if corners form a reasonable quadrilateral
            val area = calculateBaseArea(corners)
            area > 0.0001f // Minimum area threshold

        } catch (e: Exception) {
            Log.w(TAG, "Error validating corners", e)
            false
        }
    }
}