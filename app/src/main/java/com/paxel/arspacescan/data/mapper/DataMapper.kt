package com.paxel.arspacescan.data.mapper

import android.util.Log
import com.paxel.arspacescan.data.model.MeasurementResult
import com.paxel.arspacescan.data.model.PackageMeasurement
import com.paxel.arspacescan.data.model.BoxResult

/**
 * Data mapping functions untuk konversi antar model layers
 */

/**
 * Convert UI model ke Database entity
 */
fun MeasurementResult.toPackageMeasurement(): PackageMeasurement {
    // Validation sebelum mapping
    require(this.isValid()) { "Invalid MeasurementResult data: $this" }

    return PackageMeasurement(
        id = this.id,
        packageName = this.packageName.takeIf { it.isNotBlank() } ?: "",
        declaredSize = this.declaredSize.takeIf { it.isNotBlank() } ?: "",
        width = this.width,
        height = this.height,
        depth = this.depth,
        volume = this.volume,
        timestamp = this.timestamp,
        isValidated = false, // Default untuk new measurements
        imagePath = this.imagePath,
        packageSizeCategory = this.packageSizeCategory,
        estimatedPrice = this.estimatedPrice
    )
}

/**
 * Convert Database entity ke UI model
 */
fun PackageMeasurement.toMeasurementResult(): MeasurementResult {
    // Validation sebelum mapping
    require(this.isValidMeasurement()) { "Invalid PackageMeasurement data: $this" }

    return MeasurementResult(
        id = this.id,
        packageName = this.packageName,
        declaredSize = this.declaredSize,
        width = this.width,
        height = this.height,
        depth = this.depth,
        volume = this.volume,
        timestamp = this.timestamp,
        imagePath = this.imagePath,
        packageSizeCategory = this.packageSizeCategory,
        estimatedPrice = this.estimatedPrice
    )
}

/**
 * Convert AR calculation result ke UI model
 */
fun BoxResult.toMeasurementResult(
    packageName: String = "",
    declaredSize: String = "",
    imagePath: String? = null,
    timestamp: Long = System.currentTimeMillis()
): MeasurementResult {
    return MeasurementResult(
        id = 0, // Will be auto-generated when saved to DB
        packageName = packageName,
        declaredSize = declaredSize,
        width = this.width,
        height = this.height,
        depth = this.depth,
        volume = this.volume,
        timestamp = timestamp,
        imagePath = imagePath,
        packageSizeCategory = "Tidak Diketahui", // Will be calculated later
        estimatedPrice = 0 // Will be calculated later
    )
}

/**
 * Convert BoxResult to PackageMeasurement directly
 */
fun BoxResult.toPackageMeasurement(
    packageName: String = "",
    declaredSize: String = "",
    imagePath: String? = null,
    timestamp: Long = System.currentTimeMillis()
): PackageMeasurement {
    return PackageMeasurement(
        id = 0, // Will be auto-generated when saved to DB
        packageName = packageName,
        declaredSize = declaredSize,
        width = this.width,
        height = this.height,
        depth = this.depth,
        volume = this.volume,
        timestamp = timestamp,
        isValidated = false,
        imagePath = imagePath,
        packageSizeCategory = "Tidak Diketahui", // Will be calculated later
        estimatedPrice = 0 // Will be calculated later
    )
}

/**
 * Helper function: Create MeasurementResult dari raw measurement data
 */
fun createMeasurementResult(
    width: Float,
    height: Float,
    depth: Float,
    packageName: String = "",
    declaredSize: String = "",
    imagePath: String? = null
): MeasurementResult {
    require(width > 0 && height > 0 && depth > 0) {
        "Invalid dimensions: width=$width, height=$height, depth=$depth"
    }

    val volume = width * height * depth

    return MeasurementResult(
        id = 0,
        packageName = packageName,
        declaredSize = declaredSize,
        width = width,
        height = height,
        depth = depth,
        volume = volume,
        timestamp = System.currentTimeMillis(),
        imagePath = imagePath,
        packageSizeCategory = "Tidak Diketahui",
        estimatedPrice = 0
    )
}

/**
 * Batch mapping functions
 */
fun List<PackageMeasurement>.toMeasurementResults(): List<MeasurementResult> {
    return this.mapNotNull { packageMeasurement ->
        try {
            packageMeasurement.toMeasurementResult()
        } catch (e: Exception) {
            // Log error but continue with other items
            Log.w("DataMapper", "Failed to convert PackageMeasurement: $packageMeasurement", e)
            null
        }
    }
}

fun List<MeasurementResult>.toPackageMeasurements(): List<PackageMeasurement> {
    return this.mapNotNull { measurementResult ->
        try {
            measurementResult.toPackageMeasurement()
        } catch (e: Exception) {
            // Log error but continue with other items
            Log.w("DataMapper", "Failed to convert MeasurementResult: $measurementResult", e)
            null
        }
    }
}