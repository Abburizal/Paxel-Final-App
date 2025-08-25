package com.paxel.arspacescan.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

// KODE BARU - Nama field lebih konsisten
@Entity(tableName = "package_measurements")
data class PackageMeasurement(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val packageName: String,
    val declaredSize: String = "",
    val width: Float,           // lebar dalam meter
    val height: Float,          // tinggi dalam meter
    val depth: Float,           // panjang/kedalaman dalam meter
    val volume: Float,          // volume dalam m³
    val timestamp: Long,        // waktu pengukuran
    val isValidated: Boolean = false,
    val imagePath: String? = null, // Menyimpan URI/path ke file gambar dokumentasi

    // --- TAMBAHKAN DUA KOLOM INI ---
    val packageSizeCategory: String = "Tidak Diketahui",
    val estimatedPrice: Int = 0
)
