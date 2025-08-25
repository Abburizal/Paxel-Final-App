package com.paxel.arspacescan.util

import com.google.ar.sceneform.math.Vector3
import kotlin.math.acos
import kotlin.math.roundToInt

/**
 * Hasil dari validasi sudut.
 * @param isValid True jika semua sudut berada dalam rentang yang diterima.
 * @param problematicAngles Daftar sudut (dalam derajat) yang berada di luar rentang.
 */
data class AngleValidationResult(
    val isValid: Boolean,
    val problematicAngles: List<Int> = emptyList()
)

/**
 * Object utilitas untuk memvalidasi sudut dari 4 titik dasar pengukuran.
 */
object AngleValidator {

    private const val MIN_ANGLE_DEGREES = 80.0
    private const val MAX_ANGLE_DEGREES = 100.0

    /**
     * Menghitung sudut yang dibentuk oleh tiga titik (sudut di titik B).
     * Rumus: arccos((BA · BC) / (|BA| * |BC|))
     * @param pA Titik pertama.
     * @param pB Titik tengah (tempat sudut diukur).
     * @param pC Titik ketiga.
     * @return Sudut dalam derajat.
     */
    private fun calculateAngle(pA: Vector3, pB: Vector3, pC: Vector3): Double {
        val vectorBA = Vector3.subtract(pA, pB)
        val vectorBC = Vector3.subtract(pC, pB)

        val dotProduct = Vector3.dot(vectorBA, vectorBC)
        val magnitudeBA = vectorBA.length()
        val magnitudeBC = vectorBC.length()

        // Hindari pembagian dengan nol jika ada titik yang tumpang tindih
        if (magnitudeBA == 0f || magnitudeBC == 0f) {
            return 0.0
        }

        val cosTheta = dotProduct / (magnitudeBA * magnitudeBC)
        // Clamp value to prevent domain error in acos for values slightly out of [-1, 1]
        val clampedCosTheta = cosTheta.coerceIn(-1.0f, 1.0f)

        val angleRadians = acos(clampedCosTheta)
        return Math.toDegrees(angleRadians.toDouble())
    }

    /**
     * Memvalidasi 4 sudut dari alas paket.
     * @param corners Daftar 4 Vector3 yang merepresentasikan sudut A, B, C, D.
     * @return AngleValidationResult yang berisi status validasi.
     */
    fun validateBaseAngles(corners: List<Vector3>): AngleValidationResult {
        if (corners.size < 4) {
            return AngleValidationResult(isValid = false)
        }

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

        val problematic = angles.filter {
            it < MIN_ANGLE_DEGREES || it > MAX_ANGLE_DEGREES
        }.map { it.roundToInt() }

        return if (problematic.isEmpty()) {
            AngleValidationResult(isValid = true)
        } else {
            AngleValidationResult(isValid = false, problematicAngles = problematic)
        }
    }
}