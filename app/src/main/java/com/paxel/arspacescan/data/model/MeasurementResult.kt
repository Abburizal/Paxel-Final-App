package com.paxel.arspacescan.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class MeasurementResult(
    val id: Long = 0,
    val width: Float,           // lebar dalam meter
    val height: Float,          // tinggi dalam meter
    val depth: Float,           // panjang dalam meter
    val volume: Float,          // volume dalam m³
    val timestamp: Long,        // waktu pengukuran
    val packageName: String? = null,    // nama paket
    val declaredSize: String? = null,   // ukuran yang dinyatakan
    val imagePath: String? = null       // URI/path ke file gambar dokumentasi
) : Parcelable
