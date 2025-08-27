package com.paxel.arspacescan.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Database entity untuk menyimpan hasil pengukuran paket
 */
@Entity(tableName = "package_measurements")
data class PackageMeasurement(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // Basic package info
    val packageName: String = "",
    val declaredSize: String = "",

    // Measurement data - dalam meter
    val width: Float,           // lebar dalam meter
    val height: Float,          // tinggi dalam meter
    val depth: Float,           // panjang/kedalaman dalam meter
    val volume: Float,          // volume dalam m³

    // Metadata
    val timestamp: Long,        // waktu pengukuran (Unix timestamp)
    val isValidated: Boolean = false,
    val imagePath: String? = null,      // URI/path ke file gambar dokumentasi

    // Price estimation fields - added in migration 3_4
    val packageSizeCategory: String = "Tidak Diketahui",
    val estimatedPrice: Int = 0         // dalam Rupiah
) {
    /**
     * Validation helper - check if measurement data is valid
     */
    fun isValidMeasurement(): Boolean {
        return width > 0 && height > 0 && depth > 0 && volume > 0 && timestamp > 0
    }

    /**
     * Get formatted dimensions string for display
     */
    fun getFormattedDimensions(): String {
        return String.format("%.2f × %.2f × %.2f cm",
            width * 100, height * 100, depth * 100)
    }

    /**
     * Get volume in cm³ for display
     */
    fun getVolumeCm3(): Float {
        return volume * 1_000_000
    }

    /**
     * Get dimensions in centimeters
     */
    fun getDimensionsCm(): Triple<Float, Float, Float> {
        return Triple(width * 100, height * 100, depth * 100)
    }
}