package com.paxel.arspacescan.util

import android.util.Log
import com.google.ar.sceneform.math.Vector3
import kotlin.math.acos
import kotlin.math.roundToInt

/**
 * Hasil dari validasi sudut.
 */
data class AngleValidationResult(
    val isValid: Boolean,
    val problematicAngles: List<Int> = emptyList(),
    val averageAngle: Double = 90.0,
    val confidence: Float = 1.0f
)

/**
 * Object utilitas untuk memvalidasi sudut dari 4 titik dasar pengukuran.
 */
object AngleValidator {

    private const val TAG = "AngleValidator"
    private const val MIN_ANGLE_DEGREES = 80.0
    private const val MAX_ANGLE_DEGREES = 100.0
    private const val IDEAL_ANGLE_DEGREES = 90.0

    /**
     * Menghitung sudut yang dibentuk oleh tiga titik (sudut di titik B).
     * Rumus: arccos((BA · BC) / (|BA| * |BC|))
     */
    private fun calculateAngle(pA: Vector3, pB: Vector3, pC: Vector3): Double {
        return try {
            val vectorBA = Vector3.subtract(pA, pB)
            val vectorBC = Vector3.subtract(pC, pB)

            val dotProduct = Vector3.dot(vectorBA, vectorBC)
            val magnitudeBA = vectorBA.length()
            val magnitudeBC = vectorBC.length()

            // Hindari pembagian dengan nol jika ada titik yang tumpang tindih
            if (magnitudeBA == 0f || magnitudeBC == 0f) {
                Log.w(TAG, "Zero magnitude vector detected")
                return 0.0
            }

            val cosTheta = dotProduct / (magnitudeBA * magnitudeBC)
            // Clamp value to prevent domain error in acos for values slightly out of [-1, 1]
            val clampedCosTheta = cosTheta.coerceIn(-1.0f, 1.0f)

            val angleRadians = acos(clampedCosTheta)
            Math.toDegrees(angleRadians.toDouble())

        } catch (e: Exception) {
            Log.e(TAG, "Error calculating angle", e)
            0.0
        }
    }

    /**
     * Memvalidasi 4 sudut dari alas paket.
     */
    fun validateBaseAngles(corners: List<Vector3>): AngleValidationResult {
        if (corners.size < 4) {
            return AngleValidationResult(
                isValid = false,
                problematicAngles = emptyList(),
                confidence = 0f
            )
        }

        return try {
            val pA = corners[0]
            val pB = corners[1]
            val pC = corners[2]
            val pD = corners[3]

            val angles = listOf(
                calculateAngle(pD, pA, pB), // Sudut A
                calculateAngle(pA, pB, pC), // Sudut B
                calculateAngle(pB, pC, pD), // Sudut C
                calculateAngle(pC, pD, pA)  // Sudut D
            )

            // Filter out invalid angles (0.0 from calculation errors)
            val validAngles = angles.filter { it > 0.0 }

            if (validAngles.size < 4) {
                return AngleValidationResult(
                    isValid = false,
                    problematicAngles = emptyList(),
                    confidence = 0f
                )
            }

            val problematic = validAngles.filter {
                it < MIN_ANGLE_DEGREES || it > MAX_ANGLE_DEGREES
            }.map { it.roundToInt() }

            val averageAngle = validAngles.average()

            // Calculate confidence based on how close angles are to 90 degrees
            val angleDeviations = validAngles.map { kotlin.math.abs(it - IDEAL_ANGLE_DEGREES) }
            val maxDeviation = angleDeviations.maxOrNull() ?: 0.0
            val confidence = when {
                maxDeviation <= 5.0 -> 1.0f
                maxDeviation <= 10.0 -> 0.8f
                maxDeviation <= 15.0 -> 0.6f
                else -> 0.4f
            }

            AngleValidationResult(
                isValid = problematic.isEmpty(),
                problematicAngles = problematic,
                averageAngle = averageAngle,
                confidence = confidence
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error validating base angles", e)
            AngleValidationResult(
                isValid = false,
                problematicAngles = emptyList(),
                confidence = 0f
            )
        }
    }

    /**
     * Get human-readable assessment of angle quality
     */
    fun getAngleQualityAssessment(result: AngleValidationResult): String {
        return when {
            result.confidence >= 0.9f -> "Sangat Baik - Bentuk sangat persegi"
            result.confidence >= 0.7f -> "Baik - Bentuk cukup persegi"
            result.confidence >= 0.5f -> "Cukup - Bentuk agak miring"
            else -> "Kurang - Bentuk tidak persegi"
        }
    }

    /**
     * Check if corners form approximately rectangular shape
     */
    fun isRectangularShape(corners: List<Vector3>, toleranceDegrees: Double = 10.0): Boolean {
        val result = validateBaseAngles(corners)
        return result.isValid && kotlin.math.abs(result.averageAngle - IDEAL_ANGLE_DEGREES) <= toleranceDegrees
    }
}