package com.paxel.arspacescan.data.model

/**
 * Result dari AR measurement calculation
 */
data class BoxResult(
    // Measurement data - dalam meter (sama dengan models lain)
    val width: Float,   // lebar dalam meter
    val height: Float,  // tinggi dalam meter
    val depth: Float,   // panjang/kedalaman dalam meter
    val volume: Float   // volume dalam meter kubik
) {
    /**
     * Validation helper - check if calculation result is valid
     */
    fun isValid(): Boolean {
        return width > 0 && height > 0 && depth > 0 && volume > 0 &&
                width < 10f && height < 10f && depth < 10f // Reasonable max bounds
    }

    /**
     * Get dimensions in centimeters for display
     */
    fun getDimensionsCm(): Triple<Float, Float, Float> {
        return Triple(width * 100, height * 100, depth * 100)
    }

    /**
     * Get volume in cm³ for display
     */
    fun getVolumeCm3(): Float {
        return volume * 1_000_000
    }

    /**
     * Get formatted dimensions string for display
     */
    fun getFormattedDimensions(): String {
        val (w, h, d) = getDimensionsCm()
        return String.format("%.2f × %.2f × %.2f cm", w, h, d)
    }

    /**
     * Calculate measurement confidence based on aspect ratios
     */
    fun getConfidence(): Float {
        if (!isValid()) return 0f

        val dimensions = listOf(width, height, depth).sorted()
        val aspectRatio = dimensions[2] / dimensions[0] // max/min ratio

        return when {
            aspectRatio <= 2f -> 1.0f      // Perfect box shape
            aspectRatio <= 5f -> 0.8f      // Good shape
            aspectRatio <= 10f -> 0.6f     // Acceptable shape
            else -> 0.4f                   // Poor shape (very elongated)
        }
    }

    /**
     * Get quality assessment string
     */
    fun getQualityAssessment(): String {
        val confidence = getConfidence()
        return when {
            confidence >= 0.9f -> "Sangat Baik"
            confidence >= 0.7f -> "Baik"
            confidence >= 0.5f -> "Cukup"
            else -> "Rendah"
        }
    }
}