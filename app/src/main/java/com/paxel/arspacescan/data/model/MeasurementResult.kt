package com.paxel.arspacescan.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * UI model untuk transfer measurement data antar activities
 */
@Parcelize
data class MeasurementResult(
    val id: Long = 0,

    // Basic package info
    val packageName: String = "",
    val declaredSize: String = "",

    // Measurement data - dalam meter (sama seperti database)
    val width: Float,           // lebar dalam meter
    val height: Float,          // tinggi dalam meter
    val depth: Float,           // panjang dalam meter
    val volume: Float,          // volume dalam m³

    // Metadata
    val timestamp: Long,        // waktu pengukuran
    val imagePath: String? = null,      // URI/path ke file gambar dokumentasi

    // Price estimation fields
    val packageSizeCategory: String = "Tidak Diketahui",
    val estimatedPrice: Int = 0
) : Parcelable {

    /**
     * Validation helper - check if measurement data is valid
     */
    fun isValid(): Boolean {
        return width > 0 && height > 0 && depth > 0 && volume > 0 && timestamp > 0
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
     * Get formatted display string for sharing
     */
    fun getDisplayString(): String {
        val (w, h, d) = getDimensionsCm()
        return "Paket: $packageName | Dimensi: %.2f × %.2f × %.2f cm | Volume: %.2f cm³".format(w, h, d, getVolumeCm3())
    }

    /**
     * Get formatted dimensions string
     */
    fun getFormattedDimensions(): String {
        val (w, h, d) = getDimensionsCm()
        return String.format("%.2f × %.2f × %.2f cm", w, h, d)
    }
}