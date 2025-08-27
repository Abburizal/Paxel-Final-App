package com.paxel.arspacescan.util

import android.util.Log
import com.paxel.arspacescan.data.model.MeasurementResult
import java.text.DecimalFormat
import java.util.*
import kotlin.math.abs

/**
 * ✅ ENHANCED: Package size validator with improved accuracy, validation, and error handling
 */
object PackageSizeValidator {

    private const val TAG = "PackageSizeValidator"

    data class ValidationResult(
        val category: String,
        val estimatedPrice: Int,
        val isValid: Boolean = true,
        val warnings: List<String> = emptyList(),
        val confidence: Float = 1.0f,
        val accuracyLevel: AccuracyLevel = AccuracyLevel.HIGH
    )

    enum class AccuracyLevel {
        HIGH,    // Very reliable measurement
        MEDIUM,  // Good measurement with minor issues
        LOW,     // Measurement has significant issues
        POOR     // Measurement may be unreliable
    }

    // ✅ ENHANCED: More granular size categories with realistic pricing
    private val sizeCategories = listOf(
        SizeCategory("Sangat Kecil", 0, 1000, 8000, "< 1L"),        // 0-1000 cm³
        SizeCategory("Kecil", 1001, 3000, 12000, "1-3L"),           // 1001-3000 cm³
        SizeCategory("Sedang Kecil", 3001, 8000, 15000, "3-8L"),    // 3001-8000 cm³
        SizeCategory("Sedang", 8001, 20000, 20000, "8-20L"),        // 8001-20000 cm³
        SizeCategory("Sedang Besar", 20001, 40000, 25000, "20-40L"), // 20001-40000 cm³
        SizeCategory("Besar", 40001, 80000, 30000, "40-80L"),       // 40001-80000 cm³
        SizeCategory("Sangat Besar", 80001, 150000, 40000, "80-150L"), // 80001-150000 cm³
        SizeCategory("Extra Besar", 150001, Int.MAX_VALUE, 50000, "> 150L") // >150000 cm³
    )

    private data class SizeCategory(
        val name: String,
        val minVolume: Int,     // cm³
        val maxVolume: Int,     // cm³
        val price: Int,         // Rupiah
        val volumeRange: String // Display string
    )

    /**
     * ✅ ENHANCED: Main validation function with comprehensive analysis
     */
    fun validate(result: MeasurementResult): ValidationResult {
        return try {
            val volumeCm3 = (result.volume * 1_000_000).toInt()

            Log.d(TAG, "Validating package - Dimensions: ${result.getFormattedDimensions()}, Volume: $volumeCm3 cm³")

            // ✅ VALIDATION: Comprehensive dimension and measurement validation
            val validationIssues = validateMeasurementQuality(result)

            // ✅ ACCURACY: Calculate measurement accuracy level
            val accuracyLevel = determineAccuracyLevel(validationIssues)

            // ✅ CONFIDENCE: Calculate confidence based on measurement quality
            val confidence = calculateMeasurementConfidence(result, validationIssues, accuracyLevel)

            // ✅ CATEGORIZATION: Find matching category with volume validation
            val category = findCategoryForVolume(volumeCm3)

            // ✅ PRICING: Calculate price with accuracy adjustments
            val adjustedPrice = calculateAdjustedPrice(category, accuracyLevel, confidence)

            ValidationResult(
                category = category.name,
                estimatedPrice = adjustedPrice,
                isValid = validationIssues.criticalIssues.isEmpty(),
                warnings = validationIssues.allWarnings,
                confidence = confidence,
                accuracyLevel = accuracyLevel
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error validating package size", e)
            ValidationResult(
                category = "Tidak Diketahui",
                estimatedPrice = 0,
                isValid = false,
                warnings = listOf("Terjadi kesalahan saat validasi"),
                confidence = 0.0f,
                accuracyLevel = AccuracyLevel.POOR
            )
        }
    }

    /**
     * ✅ ENHANCED: Comprehensive measurement quality validation
     */
    private fun validateMeasurementQuality(result: MeasurementResult): ValidationIssues {
        val criticalIssues = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val info = mutableListOf<String>()

        val widthCm = result.width * 100
        val heightCm = result.height * 100
        val depthCm = result.depth * 100

        // ✅ CRITICAL: Check minimum dimensions
        if (widthCm < 0.5f || heightCm < 0.5f || depthCm < 0.5f) {
            criticalIssues.add("Dimensi terlalu kecil untuk diukur akurat (minimum 0.5cm)")
        }

        // ✅ CRITICAL: Check maximum dimensions
        if (widthCm > 200 || heightCm > 200 || depthCm > 200) {
            criticalIssues.add("Dimensi melebihi batas maksimum (200cm)")
        }

        // ✅ WARNING: Check for very thin dimensions
        val minDimension = minOf(widthCm, heightCm, depthCm)
        if (minDimension < 1.0f && minDimension >= 0.5f) {
            warnings.add("Ada dimensi yang sangat tipis (${String.format(Locale.US, "%.1f", minDimension)}cm), hasil mungkin kurang akurat")
        }

        // ✅ WARNING: Check aspect ratio for unusual shapes
        val maxDimension = maxOf(widthCm, heightCm, depthCm)
        if (minDimension > 0) {
            val aspectRatio = maxDimension / minDimension
            when {
                aspectRatio > 50 -> criticalIssues.add("Bentuk paket sangat ekstrim (rasio ${String.format(Locale.US, "%.0f", aspectRatio)}:1)")
                aspectRatio > 20 -> warnings.add("Bentuk paket tidak biasa (rasio ${String.format(Locale.US, "%.0f", aspectRatio)}:1), hasil mungkin kurang akurat")
                aspectRatio > 10 -> info.add("Paket memiliki bentuk memanjang (rasio ${String.format(Locale.US, "%.0f", aspectRatio)}:1)")
            }
        }

        // ✅ WARNING: Check for extremely large packages
        if (widthCm > 150 || heightCm > 150 || depthCm > 150) {
            warnings.add("Paket sangat besar, konfirmasi dengan customer service")
        }

        // ✅ INFO: Volume consistency check
        val calculatedVolume = widthCm * heightCm * depthCm
        val reportedVolume = result.getVolumeCm3()
        val volumeDifference = abs(calculatedVolume - reportedVolume)

        if (volumeDifference > reportedVolume * 0.1) { // More than 10% difference
            warnings.add("Inkonsistensi volume terdeteksi, periksa kembali pengukuran")
        }

        return ValidationIssues(criticalIssues, warnings, info)
    }

    private data class ValidationIssues(
        val criticalIssues: List<String>,
        val warnings: List<String>,
        val info: List<String>
    ) {
        val allWarnings: List<String> get() = criticalIssues + warnings + info
    }

    /**
     * ✅ ENHANCED: Determine measurement accuracy level
     */
    private fun determineAccuracyLevel(issues: ValidationIssues): AccuracyLevel {
        return when {
            issues.criticalIssues.isNotEmpty() -> AccuracyLevel.POOR
            issues.warnings.size >= 3 -> AccuracyLevel.LOW
            issues.warnings.size >= 2 -> AccuracyLevel.MEDIUM
            issues.warnings.size == 1 -> AccuracyLevel.MEDIUM
            else -> AccuracyLevel.HIGH
        }
    }

    /**
     * ✅ ENHANCED: Calculate comprehensive measurement confidence
     */
    private fun calculateMeasurementConfidence(
        result: MeasurementResult,
        issues: ValidationIssues,
        accuracyLevel: AccuracyLevel
    ): Float {
        var confidence = 1.0f

        // ✅ ISSUES: Reduce confidence based on validation issues
        confidence -= issues.criticalIssues.size * 0.4f
        confidence -= issues.warnings.size * 0.15f
        confidence -= issues.info.size * 0.05f

        // ✅ ACCURACY: Adjust based on accuracy level
        confidence *= when (accuracyLevel) {
            AccuracyLevel.HIGH -> 1.0f
            AccuracyLevel.MEDIUM -> 0.8f
            AccuracyLevel.LOW -> 0.6f
            AccuracyLevel.POOR -> 0.3f
        }

        // ✅ DIMENSIONS: Check dimension ratios
        val widthCm = result.width * 100
        val heightCm = result.height * 100
        val depthCm = result.depth * 100

        val dimensions = listOf(widthCm, heightCm, depthCm).sorted()
        if (dimensions[2] > 0 && dimensions[0] > 0) {
            val aspectRatio = dimensions[2] / dimensions[0]
            confidence *= when {
                aspectRatio <= 3f -> 1.0f
                aspectRatio <= 10f -> 0.9f
                aspectRatio <= 20f -> 0.7f
                aspectRatio <= 50f -> 0.5f
                else -> 0.3f
            }
        }

        // ✅ SIZE: Confidence based on size reasonableness
        val volume = result.getVolumeCm3()
        val sizeConfidence = when {
            volume < 100 -> 0.7f      // Very small, harder to measure accurately
            volume > 500000 -> 0.8f   // Very large, may have tracking issues
            else -> 1.0f              // Good size range
        }
        confidence *= sizeConfidence

        return confidence.coerceIn(0f, 1f)
    }

    /**
     * ✅ ENHANCED: Find category with better volume matching
     */
    private fun findCategoryForVolume(volumeCm3: Int): SizeCategory {
        val category = sizeCategories.firstOrNull { cat ->
            volumeCm3 >= cat.minVolume && volumeCm3 <= cat.maxVolume
        } ?: sizeCategories.last() // Default to largest category if over limit

        Log.d(TAG, "Volume ${volumeCm3}cm³ categorized as: ${category.name} (${category.volumeRange})")

        return category
    }

    /**
     * ✅ ENHANCED: Calculate price with accuracy adjustments
     */
    private fun calculateAdjustedPrice(
        category: SizeCategory,
        accuracyLevel: AccuracyLevel,
        confidence: Float
    ): Int {
        var price = category.price

        // ✅ ACCURACY: Adjust price based on measurement accuracy
        val accuracyMultiplier = when (accuracyLevel) {
            AccuracyLevel.HIGH -> 1.0f
            AccuracyLevel.MEDIUM -> 0.95f
            AccuracyLevel.LOW -> 0.9f
            AccuracyLevel.POOR -> 0.8f
        }

        // ✅ CONFIDENCE: Further adjust based on confidence
        val confidenceMultiplier = when {
            confidence >= 0.9f -> 1.0f
            confidence >= 0.7f -> 0.98f
            confidence >= 0.5f -> 0.95f
            else -> 0.9f
        }

        price = (price * accuracyMultiplier * confidenceMultiplier).toInt()

        // ✅ ROUNDING: Round to nearest 1000 for cleaner pricing
        price = ((price + 500) / 1000) * 1000

        Log.d(TAG, "Price calculation: base=${category.price}, accuracy=$accuracyMultiplier, confidence=$confidenceMultiplier, final=$price")

        return maxOf(price, 1000) // Minimum price of Rp 1,000
    }

    /**
     * ✅ ENHANCED: Format price with proper Indonesian Rupiah formatting
     */
    fun formatPrice(price: Int): String {
        return when {
            price <= 0 -> "Rp0"
            price < 1000 -> "Rp$price"
            else -> try {
                val formatter = DecimalFormat("#,###")
                "Rp${formatter.format(price)}"
            } catch (e: Exception) {
                Log.w(TAG, "Error formatting price: $price", e)
                "Rp$price"
            }
        }
    }

    /**
     * ✅ ENHANCED: Get comprehensive dimension summary for display
     */
    fun getDimensionSummary(result: MeasurementResult): String {
        return try {
            val widthCm = result.width * 100
            val heightCm = result.height * 100
            val depthCm = result.depth * 100

            "${String.format(Locale.US, "%.1f", widthCm)} × ${String.format(Locale.US, "%.1f", heightCm)} × ${String.format(Locale.US, "%.1f", depthCm)} cm"
        } catch (e: Exception) {
            Log.w(TAG, "Error formatting dimensions", e)
            "Dimensi tidak tersedia"
        }
    }

    /**
     * ✅ ENHANCED: Comprehensive dimension validation
     */
    fun isValidDimensions(result: MeasurementResult): Boolean {
        return try {
            val widthCm = result.width * 100
            val heightCm = result.height * 100
            val depthCm = result.depth * 100

            // ✅ BASIC: Check positive dimensions
            if (widthCm <= 0 || heightCm <= 0 || depthCm <= 0) return false

            // ✅ RANGE: Check reasonable range (0.1cm to 300cm)
            val minDim = minOf(widthCm, heightCm, depthCm)
            val maxDim = maxOf(widthCm, heightCm, depthCm)

            minDim >= 0.1f && maxDim <= 300f

        } catch (e: Exception) {
            Log.w(TAG, "Error validating dimensions", e)
            false
        }
    }

    /**
     * ✅ ENHANCED: Get category color for UI with better color coding
     */
    fun getCategoryColor(categoryName: String): String {
        return when (categoryName.lowercase()) {
            "sangat kecil" -> "#4CAF50"      // Green
            "kecil" -> "#8BC34A"             // Light Green
            "sedang kecil" -> "#CDDC39"      // Lime
            "sedang" -> "#FFC107"            // Amber
            "sedang besar" -> "#FF9800"      // Orange
            "besar" -> "#FF5722"             // Deep Orange
            "sangat besar" -> "#F44336"      // Red
            "extra besar" -> "#E91E63"       // Pink (very large)
            else -> "#9E9E9E"                // Grey (unknown)
        }
    }

    /**
     * ✅ NEW: Get accuracy level description
     */
    fun getAccuracyDescription(accuracyLevel: AccuracyLevel): String {
        return when (accuracyLevel) {
            AccuracyLevel.HIGH -> "Akurasi Tinggi - Pengukuran sangat dapat diandalkan"
            AccuracyLevel.MEDIUM -> "Akurasi Sedang - Pengukuran cukup dapat diandalkan"
            AccuracyLevel.LOW -> "Akurasi Rendah - Pengukuran memiliki beberapa masalah"
            AccuracyLevel.POOR -> "Akurasi Buruk - Pengukuran mungkin tidak dapat diandalkan"
        }
    }

    /**
     * ✅ NEW: Get confidence level description
     */
    fun getConfidenceDescription(confidence: Float): String {
        return when {
            confidence >= 0.9f -> "Sangat Yakin - Hasil sangat dapat dipercaya"
            confidence >= 0.7f -> "Yakin - Hasil dapat dipercaya"
            confidence >= 0.5f -> "Cukup Yakin - Hasil cukup dapat dipercaya"
            confidence >= 0.3f -> "Kurang Yakin - Hasil perlu verifikasi"
            else -> "Tidak Yakin - Hasil sangat diragukan"
        }
    }

    // ✅ SIMPLIFIED: Remove unused functions to clean up warnings
    // Functions like generateValidationReport, getAvailableCategories, etc.
    // can be added back when actually needed by the UI
}