package com.paxel.arspacescan.data.mapper

import com.paxel.arspacescan.data.model.MeasurementResult
import com.paxel.arspacescan.data.model.PackageMeasurement

/**
 * Berisi fungsi ekstensi untuk mengubah (mapping) satu objek model ke objek model lain.
 * Ini membantu memisahkan model data untuk database (Entity) dari model data untuk UI (Parcelable).
 */

fun MeasurementResult.toPackageMeasurement(): PackageMeasurement {
    return PackageMeasurement(
        id = this.id,
        packageName = this.packageName ?: "",
        declaredSize = this.declaredSize ?: "",
        width = this.width,
        height = this.height,
        depth = this.depth,
        volume = this.volume,
        timestamp = this.timestamp,
        isValidated = false,
        imagePath = this.imagePath,
        // --- TAMBAHKAN MAPPING UNTUK FIELD BARU ---
        packageSizeCategory = this.packageSizeCategory,
        estimatedPrice = this.estimatedPrice
    )
}

fun PackageMeasurement.toMeasurementResult(): MeasurementResult {
    return MeasurementResult(
        id = this.id,
        width = this.width,
        height = this.height,
        depth = this.depth,
        volume = this.volume,
        timestamp = this.timestamp,
        packageName = this.packageName,
        declaredSize = this.declaredSize,
        imagePath = this.imagePath,
        // --- TAMBAHKAN MAPPING UNTUK FIELD BARU ---
        packageSizeCategory = this.packageSizeCategory,
        estimatedPrice = this.estimatedPrice
    )
}