package com.paxel.arspacescan.util

import android.content.Context
import android.util.Log
import com.paxel.arspacescan.data.model.MeasurementResult
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.*

object PackageSizeValidator {

    private const val TAG = "PackageSizeValidator"

    data class ValidationResult(
        val category: String,
        val estimatedPrice: Int,
        val isValid: Boolean = true,
        val warnings: List<String> = emptyList(),
        val confidence: Float = 1.0f
    )

    // Hardcoded size categories for reliability
    private val sizeCategories = listOf(
        SizeCategory("Sangat Kecil", 0, 1000, 10000),        // 0-1000 cm³
        SizeCategory("Kecil", 1001, 5000, 15000),            // 1001-5000 cm³
        SizeCategory("Sedang", 5001, 15000, 20000),          // 5001-15000 cm³
        SizeCategory("Besar", 15001, 30000, 25000),          // 15001-30000 cm³
        SizeCategory("Sangat Besar", 30001, 50000, 35000),   // 30001-50000 cm³
        SizeCategory("Extra Besar", 50001, Int.MAX_VALUE, 50000) // >50000 cm³
    )

    private data class SizeCategory(
        val name: String,
        val minVolume: Int,
        val maxVolume: Int,
        val price: Int
    )

    /**
     * Main validation function - synchronous and reliable
     */
    fun validate(context: Context, result: MeasurementResult): ValidationResult {
        return try {
            val volumeCm3 = (result.volume * 1_000_000).toInt()

            Log.d(TAG, "Validating package - Volume: $volumeCm3 cm³")

            // Validate dimensions are reasonable
            val warnings = validateDimensions(result)

            // Calculate confidence based on dimension ratios and warnings
            val confidence = calculateConfidence(result, warnings)

            // Find matching category
            val category = findCategoryForVolume(volumeCm3)

            ValidationResult(
                category = category.name,
                estimatedPrice = category.price,
                isValid = warnings.isEmpty(),
                warnings = warnings,
                confidence = confidence
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error during validation", e)
            ValidationResult(
                category = "Tidak Diketahui",
                estimatedPrice = 0,
                isValid = false,
                warnings = listOf("Gagal menghitung estimasi: ${e.message}"),
                confidence = 0f
            )
        }
    }

    private fun validateDimensions(result: MeasurementResult): List<String> {
        val warnings = mutableListOf<String>()
        val widthCm = result.width * 100
        val heightCm = result.height * 100
        val depthCm = result.depth * 100

        // Check minimum dimensions
        if (widthCm < 1 || heightCm < 1 || depthCm < 1) {
            warnings.add("Dimensi terlalu kecil untuk diukur akurat")
        }

        // Check maximum dimensions
        if (widthCm > 150 || heightCm > 150 || depthCm > 150) {
            warnings.add("Paket sangat besar, konfirmasi dengan customer service")
        }

        // Check aspect ratio for unusual shapes
        val maxDim = maxOf(widthCm, heightCm, depthCm)
        val minDim = minOf(widthCm, heightCm, depthCm)
        if (minDim > 0 && maxDim / minDim > 10) {
            warnings.add("Bentuk paket tidak biasa, hasil mungkin kurang akurat")
        }

        // Check for extremely thin dimensions
        if (widthCm < 0.5f || heightCm < 0.5f || depthCm < 0.5f) {
            warnings.add("Ada dimensi yang sangat tipis, periksa kembali pengukuran")
        }

        return warnings
    }

    private fun calculateConfidence(result: MeasurementResult, warnings: List<String>): Float {
        var confidence = 1.0f

        // Reduce confidence based on warnings
        confidence -= warnings.size * 0.2f

        // Check dimension ratios
        val widthCm = result.width * 100
        val heightCm = result.height * 100
        val depthCm = result.depth * 100

        val dimensions = listOf(widthCm, heightCm, depthCm).sorted()
        if (dimensions[2] > 0 && dimensions[0] > 0) {
            val aspectRatio = dimensions[2] / dimensions[0]
            when {
                aspectRatio > 20 -> confidence *= 0.3f
                aspectRatio > 10 -> confidence *= 0.5f
                aspectRatio > 5 -> confidence *= 0.7f
            }
        }

        return confidence.coerceIn(0f, 1f)
    }

    private fun findCategoryForVolume(volumeCm3: Int): SizeCategory {
        return sizeCategories.firstOrNull { category ->
            volumeCm3 >= category.minVolume && volumeCm3 <= category.maxVolume
        } ?: sizeCategories.last() // Default to largest category if over limit
    }

    /**
     * Format price with proper Indonesian Rupiah formatting
     */
    fun formatPrice(price: Int): String {
        return if (price <= 0) {
            "Rp0"
        } else {
            try {
                val formatter = DecimalFormat("#,###")
                "Rp${formatter.format(price)}"
            } catch (e: Exception) {
                Log.w(TAG, "Error formatting price: $price", e)
                "Rp$price"
            }
        }
    }

    /**
     * Get dimension summary for display
     */
    fun getDimensionSummary(result: MeasurementResult): String {
        return try {
            val widthCm = result.width * 100
            val heightCm = result.height * 100
            val depthCm = result.depth * 100

            "${String.format("%.1f", widthCm)} × ${String.format("%.1f", heightCm)} × ${String.format("%.1f", depthCm)} cm"
        } catch (e: Exception) {
            Log.w(TAG, "Error formatting dimensions", e)
            "Dimensi tidak tersedia"
        }
    }

    /**
     * Check if dimensions are within reasonable bounds
     */
    fun isValidDimensions(result: MeasurementResult): Boolean {
        return try {
            val widthCm = result.width * 100
            val heightCm = result.height * 100
            val depthCm = result.depth * 100

            widthCm > 0.1 && heightCm > 0.1 && depthCm > 0.1 &&
                    widthCm < 200 && heightCm < 200 && depthCm < 200
        } catch (e: Exception) {
            Log.w(TAG, "Error validating dimensions", e)
            false
        }
    }

    /**
     * Get all available categories for UI
     */
    fun getAvailableCategories(): List<Pair<String, String>> {
        return sizeCategories.map { category ->
            val volumeRange = if (category.maxVolume == Int.MAX_VALUE) {
                "> ${formatVolume(category.minVolume)}"
            } else {
                "${formatVolume(category.minVolume)} - ${formatVolume(category.maxVolume)}"
            }
            category.name to volumeRange
        }
    }

    private fun formatVolume(volume: Int): String {
        return when {
            volume >= 1000000 -> "${volume / 1000000}L"
            volume >= 1000 -> "${volume / 1000}L"
            else -> "${volume}cm³"
        }
    }

    /**
     * Get price range for a category
     */
    fun getPriceRangeForCategory(categoryName: String): Pair<Int, Int> {
        val category = sizeCategories.find { it.name == categoryName }
        return if (category != null) {
            Pair(category.price - 5000, category.price + 5000)
        } else {
            Pair(0, 0)
        }
    }

    /**
     * Get category color for UI (returns color resource name)
     */
    fun getCategoryColor(categoryName: String): String {
        return when (categoryName) {
            "Sangat Kecil" -> "#4CAF50"  // Green
            "Kecil" -> "#8BC34A"         // Light Green
            "Sedang" -> "#FFC107"        // Amber
            "Besar" -> "#FF9800"         // Orange
            "Sangat Besar" -> "#FF5722"  // Deep Orange
            "Extra Besar" -> "#F44336"   // Red
            else -> "#9E9E9E"            // Grey
        }
    }
}