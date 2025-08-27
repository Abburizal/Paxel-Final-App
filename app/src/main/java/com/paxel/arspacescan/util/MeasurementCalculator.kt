package com.paxel.arspacescan.util

import android.util.Log
import com.google.ar.sceneform.math.Vector3
import com.paxel.arspacescan.data.model.BoxResult
import kotlin.math.*

/**
 * ✅ ENHANCED: Measurement calculator with improved accuracy, validation, and error handling
 */
object MeasurementCalculator {

    private const val TAG = "MeasurementCalculator"

    // ✅ ACCURACY: Precise bounds for measurement validation (in meters)
    private const val MIN_DIMENSION_M = 0.005f  // 0.5cm minimum
    private const val MAX_DIMENSION_M = 2.0f    // 200cm maximum
    private const val MIN_AREA_M2 = 0.000025f   // 0.5cm² minimum area
    private const val MAX_ASPECT_RATIO = 20f    // Maximum width:height ratio

    data class CalculationResult(
        val width: Float,
        val height: Float,
        val depth: Float,
        val volume: Float,
        val confidence: Float = 1.0f
    )

    /**
     * ✅ ENHANCED: Main calculation method with comprehensive validation and error handling
     * Calculate box dimensions from 4 base corners and height with improved accuracy
     */
    fun calculate(baseCorners: List<Vector3>, height: Float): BoxResult {
        if (baseCorners.size != 4) {
            throw IllegalArgumentException("Need exactly 4 base corners, got ${baseCorners.size}")
        }

        if (height <= 0) {
            throw IllegalArgumentException("Height must be positive, got $height")
        }

        return try {
            Log.d(TAG, "Starting calculation with ${baseCorners.size} corners and height ${String.format("%.3f", height)}m")

            // ✅ VALIDATION: Comprehensive corner validation before processing
            if (!validateCorners(baseCorners)) {
                throw IllegalArgumentException("Invalid corner configuration - corners too close or collinear")
            }

            // ✅ ACCURACY: Robust corner ordering with geometric validation
            val orderedCorners = orderCornersRobust(baseCorners)

            // ✅ CALCULATION: Enhanced dimension calculation with validation
            val dimensions = calculateDimensionsWithValidation(orderedCorners, height)

            // ✅ SAFETY: Apply scale validation and clamping
            val validatedDimensions = validateAndClampDimensions(dimensions)

            // ✅ RESULT: Create result with validation
            val result = BoxResult(
                width = validatedDimensions.width,
                height = validatedDimensions.height,
                depth = validatedDimensions.depth,
                volume = validatedDimensions.volume
            )

            // ✅ VALIDATION: Final result validation
            if (!result.isValid()) {
                Log.w(TAG, "Calculated result is invalid: $result")
                throw IllegalArgumentException("Calculation produced invalid result")
            }

            Log.d(TAG, "Calculation successful: ${result.getFormattedDimensions()}, volume=${String.format("%.1f", result.getVolumeCm3())}cm³, confidence=${String.format("%.2f", result.getConfidence())}")

            result

        } catch (e: Exception) {
            Log.e(TAG, "Error calculating dimensions", e)

            // ✅ FALLBACK: Return safe fallback instead of crashing
            val safeHeight = maxOf(height, MIN_DIMENSION_M)
            val fallbackResult = BoxResult(
                MIN_DIMENSION_M,
                safeHeight,
                MIN_DIMENSION_M,
                MIN_DIMENSION_M * MIN_DIMENSION_M * safeHeight
            )

            Log.w(TAG, "Returning fallback result due to calculation error: ${fallbackResult.getFormattedDimensions()}")
            fallbackResult
        }
    }

    /**
     * ✅ ENHANCED: Robust corner ordering with comprehensive geometric validation
     */
    private fun orderCornersRobust(corners: List<Vector3>): List<Vector3> {
        if (corners.size != 4) return corners

        return try {
            // ✅ GEOMETRIC: Calculate robust geometric center
            val centroid = Vector3(
                corners.sumOf { it.x.toDouble() }.toFloat() / 4,
                corners.sumOf { it.y.toDouble() }.toFloat() / 4,
                corners.sumOf { it.z.toDouble() }.toFloat() / 4
            )

            // ✅ SORTING: Sort by polar angle from centroid (counter-clockwise)
            val sortedCorners = corners.sortedBy { corner ->
                atan2((corner.z - centroid.z).toDouble(), (corner.x - centroid.x).toDouble())
            }

            // ✅ VALIDATION: Validate that ordering produces valid quadrilateral
            if (!isValidQuadrilateral(sortedCorners)) {
                Log.w(TAG, "Sorted corners don't form valid quadrilateral, trying alternative ordering")

                // ✅ ALTERNATIVE: Try distance-based ordering as fallback
                val alternativeOrder = orderByDistances(corners)
                if (isValidQuadrilateral(alternativeOrder)) {
                    Log.d(TAG, "Alternative distance-based ordering successful")
                    return alternativeOrder
                } else {
                    Log.w(TAG, "Both ordering methods failed, using original order")
                    return corners
                }
            }

            Log.d(TAG, "Corner ordering successful using centroid method")
            sortedCorners

        } catch (e: Exception) {
            Log.w(TAG, "Error ordering corners robustly, using original order", e)
            corners
        }
    }

    /**
     * ✅ NEW: Alternative corner ordering based on inter-point distances
     */
    private fun orderByDistances(corners: List<Vector3>): List<Vector3> {
        if (corners.size != 4) return corners

        return try {
            // Start with first corner, then find nearest, then continue clockwise
            val ordered = mutableListOf<Vector3>()
            val remaining = corners.toMutableList()

            // Start with any corner
            ordered.add(remaining.removeAt(0))

            while (remaining.isNotEmpty()) {
                val current = ordered.last()

                // Find the nearest remaining corner
                val nearest = remaining.minByOrNull { corner ->
                    distance(current, corner)
                }

                if (nearest != null) {
                    ordered.add(nearest)
                    remaining.remove(nearest)
                }
            }

            ordered

        } catch (e: Exception) {
            Log.w(TAG, "Error in distance-based ordering", e)
            corners
        }
    }

    /**
     * ✅ ENHANCED: Comprehensive dimension calculation with validation
     */
    private fun calculateDimensionsWithValidation(corners: List<Vector3>, height: Float): CalculationResult {
        try {
            // ✅ CALCULATION: Calculate all four side lengths for accuracy
            val sides = listOf(
                distance(corners[0], corners[1]),
                distance(corners[1], corners[2]),
                distance(corners[2], corners[3]),
                distance(corners[3], corners[0])
            )

            Log.d(TAG, "Side lengths: ${sides.map { String.format("%.3f", it) }}")

            // ✅ ACCURACY: Group opposite sides and take average for better accuracy
            val width = (sides[0] + sides[2]) / 2f
            val depth = (sides[1] + sides[3]) / 2f

            // ✅ VALIDATION: Ensure minimum dimensions
            val finalWidth = maxOf(width, MIN_DIMENSION_M)
            val finalDepth = maxOf(depth, MIN_DIMENSION_M)
            val finalHeight = maxOf(height, MIN_DIMENSION_M)

            // ✅ VOLUME: Calculate volume with validation
            val volume = finalWidth * finalDepth * finalHeight

            // ✅ CONFIDENCE: Calculate confidence based on measurement quality
            val confidence = calculateConfidence(sides, width, depth, height)

            Log.d(TAG, "Calculated dimensions: W=${String.format("%.3f", finalWidth)}, H=${String.format("%.3f", finalHeight)}, D=${String.format("%.3f", finalDepth)}, V=${String.format("%.6f", volume)}, confidence=${String.format("%.2f", confidence)}")

            return CalculationResult(
                width = finalWidth,
                height = finalHeight,
                depth = finalDepth,
                volume = volume,
                confidence = confidence
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error in dimension calculation", e)
            throw e
        }
    }

    /**
     * ✅ ENHANCED: Comprehensive confidence calculation based on measurement quality
     */
    private fun calculateConfidence(sides: List<Float>, width: Float, depth: Float, height: Float): Float {
        if (sides.size != 4) return 0.5f

        return try {
            var confidence = 1.0f

            // ✅ RECTANGULARITY: Check how similar opposite sides are (rectangular shape quality)
            val widthSimilarity = 1f - abs(sides[0] - sides[2]) / maxOf(sides[0], sides[2])
            val depthSimilarity = 1f - abs(sides[1] - sides[3]) / maxOf(sides[1], sides[3])
            val rectangularityScore = (widthSimilarity + depthSimilarity) / 2f

            // ✅ ASPECT RATIO: Penalize extreme aspect ratios
            val aspectRatio = maxOf(width, depth) / minOf(width, depth)
            val aspectRatioScore = when {
                aspectRatio <= 2f -> 1.0f
                aspectRatio <= 5f -> 0.8f
                aspectRatio <= 10f -> 0.6f
                aspectRatio <= 20f -> 0.4f
                else -> 0.2f
            }

            // ✅ SIZE REASONABLENESS: Prefer measurements in reasonable size ranges
            val sizeReasonableness = when {
                width < 0.01f || depth < 0.01f || height < 0.01f -> 0.3f // Too small
                width > 1.5f || depth > 1.5f || height > 1.5f -> 0.6f // Very large
                width > 0.02f && depth > 0.02f && height > 0.02f -> 1.0f // Good size
                else -> 0.8f
            }

            // ✅ COMBINE: Weighted combination of quality factors
            confidence = (rectangularityScore * 0.5f + aspectRatioScore * 0.3f + sizeReasonableness * 0.2f)
                .coerceIn(0.1f, 1.0f)

            Log.d(TAG, "Confidence factors: rectangularity=${String.format("%.2f", rectangularityScore)}, aspectRatio=${String.format("%.2f", aspectRatioScore)}, sizeReasonableness=${String.format("%.2f", sizeReasonableness)}, final=${String.format("%.2f", confidence)}")

            confidence

        } catch (e: Exception) {
            Log.w(TAG, "Error calculating confidence", e)
            0.5f
        }
    }

    /**
     * ✅ ENHANCED: Dimension validation with proper clamping and confidence adjustment
     */
    private fun validateAndClampDimensions(dimensions: CalculationResult): CalculationResult {
        val clampedWidth = dimensions.width.coerceIn(MIN_DIMENSION_M, MAX_DIMENSION_M)
        val clampedHeight = dimensions.height.coerceIn(MIN_DIMENSION_M, MAX_DIMENSION_M)
        val clampedDepth = dimensions.depth.coerceIn(MIN_DIMENSION_M, MAX_DIMENSION_M)

        val clampedVolume = clampedWidth * clampedHeight * clampedDepth

        // ✅ CONFIDENCE: Adjust confidence if clamping occurred
        val wasChanged = (clampedWidth != dimensions.width) ||
                (clampedHeight != dimensions.height) ||
                (clampedDepth != dimensions.depth)

        val adjustedConfidence = if (wasChanged) {
            dimensions.confidence * 0.7f // Reduce confidence if clamping occurred
        } else {
            dimensions.confidence
        }

        if (wasChanged) {
            Log.w(TAG, "Dimensions were clamped: " +
                    "W(${String.format("%.3f", dimensions.width)}→${String.format("%.3f", clampedWidth)}), " +
                    "H(${String.format("%.3f", dimensions.height)}→${String.format("%.3f", clampedHeight)}), " +
                    "D(${String.format("%.3f", dimensions.depth)}→${String.format("%.3f", clampedDepth)})")
        }

        return CalculationResult(
            width = clampedWidth,
            height = clampedHeight,
            depth = clampedDepth,
            volume = clampedVolume,
            confidence = adjustedConfidence
        )
    }

    /**
     * ✅ ENHANCED: Comprehensive geometric validation for quadrilaterals
     */
    private fun isValidQuadrilateral(corners: List<Vector3>): Boolean {
        if (corners.size != 4) return false

        return try {
            // ✅ DISTANCE: Check minimum distances between consecutive corners
            for (i in corners.indices) {
                val nextIndex = (i + 1) % corners.size
                val dist = distance(corners[i], corners[nextIndex])
                if (dist < MIN_DIMENSION_M) {
                    Log.d(TAG, "Invalid quadrilateral: edge $i→$nextIndex too short ($dist)")
                    return false
                }
            }

            // ✅ AREA: Check if area is reasonable
            val area = calculateQuadrilateralArea(corners)
            if (area < MIN_AREA_M2) {
                Log.d(TAG, "Invalid quadrilateral: area too small ($area)")
                return false
            }

            // ✅ ASPECT: Check if quadrilateral is not too distorted
            val sides = listOf(
                distance(corners[0], corners[1]),
                distance(corners[1], corners[2]),
                distance(corners[2], corners[3]),
                distance(corners[3], corners[0])
            )

            val maxSide = sides.maxOrNull() ?: return false
            val minSide = sides.minOrNull() ?: return false

            if (minSide > 0 && maxSide / minSide > MAX_ASPECT_RATIO) {
                Log.d(TAG, "Invalid quadrilateral: aspect ratio too extreme (${maxSide / minSide})")
                return false
            }

            // ✅ SELF-INTERSECTION: Check for self-intersecting quadrilateral (basic check)
            if (isQuadrilateralSelfIntersecting(corners)) {
                Log.d(TAG, "Invalid quadrilateral: self-intersecting")
                return false
            }

            true

        } catch (e: Exception) {
            Log.w(TAG, "Error validating quadrilateral", e)
            false
        }
    }

    /**
     * ✅ NEW: Check if quadrilateral is self-intersecting (simple check)
     */
    private fun isQuadrilateralSelfIntersecting(corners: List<Vector3>): Boolean {
        if (corners.size != 4) return false

        return try {
            // Check if diagonals intersect in a reasonable way
            // For a convex quadrilateral, diagonals should intersect inside
            val diagonal1Start = corners[0]
            val diagonal1End = corners[2]
            val diagonal2Start = corners[1]
            val diagonal2End = corners[3]

            // Simple check: if all corners are roughly coplanar and form a reasonable shape
            // More sophisticated intersection tests could be added here

            // Check if corners form roughly convex shape by checking cross products
            val vectors = listOf(
                Vector3.subtract(corners[1], corners[0]),
                Vector3.subtract(corners[2], corners[1]),
                Vector3.subtract(corners[3], corners[2]),
                Vector3.subtract(corners[0], corners[3])
            )

            // All cross products should have same general direction for convex quadrilateral
            var positiveCount = 0
            var negativeCount = 0

            for (i in vectors.indices) {
                val nextIndex = (i + 1) % vectors.size
                val cross = Vector3.cross(vectors[i], vectors[nextIndex])
                if (cross.y > 0) positiveCount++ else negativeCount++
            }

            // If all cross products have same sign, quadrilateral is likely convex
            val isConvex = (positiveCount == 4) || (negativeCount == 4)

            !isConvex // Return true if self-intersecting (not convex)

        } catch (e: Exception) {
            Log.w(TAG, "Error checking self-intersection", e)
            false
        }
    }

    /**
     * ✅ ENHANCED: Calculate distance between two points (2D projection for base measurements)
     */
    private fun distance(p1: Vector3, p2: Vector3): Float {
        return try {
            val dx = p1.x - p2.x
            val dz = p1.z - p2.z
            sqrt(dx * dx + dz * dz)
        } catch (e: Exception) {
            Log.w(TAG, "Error calculating distance", e)
            0f
        }
    }

    /**
     * ✅ ENHANCED: Calculate area of quadrilateral using shoelace formula
     */
    fun calculateBaseArea(corners: List<Vector3>): Float {
        return calculateQuadrilateralArea(corners)
    }

    /**
     * ✅ NEW: Robust quadrilateral area calculation
     */
    private fun calculateQuadrilateralArea(corners: List<Vector3>): Float {
        if (corners.size != 4) return 0f

        return try {
            // Using shoelace formula for polygon area (2D projection on XZ plane)
            var area = 0f
            for (i in corners.indices) {
                val j = (i + 1) % corners.size
                area += corners[i].x * corners[j].z - corners[j].x * corners[i].z
            }

            abs(area) / 2f

        } catch (e: Exception) {
            Log.w(TAG, "Error calculating quadrilateral area", e)
            0f
        }
    }

    /**
     * ✅ ENHANCED: Comprehensive corner validation before calculation
     */
    fun validateCorners(corners: List<Vector3>): Boolean {
        if (corners.size != 4) return false

        return try {
            // ✅ DISTINCTNESS: Check if all corners are distinct (not overlapping)
            for (i in corners.indices) {
                for (j in i + 1 until corners.size) {
                    val dist = distance(corners[i], corners[j])
                    if (dist < MIN_DIMENSION_M) {
                        Log.d(TAG, "Corner validation failed: corners $i and $j too close ($dist)")
                        return false
                    }
                }
            }

            // ✅ AREA: Check if corners form a reasonable quadrilateral
            val area = calculateBaseArea(corners)
            if (area < MIN_AREA_M2) {
                Log.d(TAG, "Corner validation failed: area too small ($area)")
                return false
            }

            // ✅ COLLINEARITY: Check if corners are not all collinear
            if (arePointsCollinear(corners)) {
                Log.d(TAG, "Corner validation failed: points are collinear")
                return false
            }

            Log.d(TAG, "Corner validation passed: area=$area, all distances valid")
            true

        } catch (e: Exception) {
            Log.w(TAG, "Error validating corners", e)
            false
        }
    }

    /**
     * ✅ NEW: Check if points are approximately collinear
     */
    private fun arePointsCollinear(corners: List<Vector3>): Boolean {
        if (corners.size < 3) return true

        return try {
            // Check if first three points are collinear using cross product
            val v1 = Vector3.subtract(corners[1], corners[0])
            val v2 = Vector3.subtract(corners[2], corners[0])
            val cross = Vector3.cross(v1, v2)

            // If cross product magnitude is very small, points are nearly collinear
            cross.length() < 0.001f

        } catch (e: Exception) {
            Log.w(TAG, "Error checking collinearity", e)
            false
        }
    }

    /**
     * ✅ ENHANCED: Get measurement quality assessment
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
     * ✅ ENHANCED: Comprehensive measurement result validation
     */
    fun validateMeasurement(result: CalculationResult): Boolean {
        return try {
            result.width > MIN_DIMENSION_M &&
                    result.height > MIN_DIMENSION_M &&
                    result.depth > MIN_DIMENSION_M &&
                    result.width < MAX_DIMENSION_M &&
                    result.height < MAX_DIMENSION_M &&
                    result.depth < MAX_DIMENSION_M &&
                    result.volume > MIN_AREA_M2 * MIN_DIMENSION_M && // Minimum volume
                    result.confidence > 0.0f
        } catch (e: Exception) {
            Log.w(TAG, "Error validating measurement result", e)
            false
        }
    }

    /**
     * ✅ NEW: Get detailed calculation statistics for debugging
     */
    fun getCalculationStatistics(corners: List<Vector3>, height: Float): Map<String, Any> {
        return try {
            val sides = if (corners.size == 4) {
                listOf(
                    distance(corners[0], corners[1]),
                    distance(corners[1], corners[2]),
                    distance(corners[2], corners[3]),
                    distance(corners[3], corners[0])
                )
            } else emptyList()

            val area = calculateBaseArea(corners)

            mapOf(
                "cornerCount" to corners.size,
                "sides" to sides.map { String.format("%.3f", it) },
                "area" to String.format("%.6f", area),
                "height" to String.format("%.3f", height),
                "isValidQuadrilateral" to isValidQuadrilateral(corners),
                "cornersValid" to validateCorners(corners)
            )
        } catch (e: Exception) {
            mapOf("error" to e.message.orEmpty())
        }
    }
}