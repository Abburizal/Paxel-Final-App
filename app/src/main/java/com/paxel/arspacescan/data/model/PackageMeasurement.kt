package com.paxel.arspacescan.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "package_measurements")
data class PackageMeasurement(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val packageName: String,
    val declaredSize: String = "",
    val measuredWidth: Float,      // lebar terukur dalam meter
    val measuredHeight: Float,     // tinggi terukur dalam meter
    val measuredDepth: Float,      // panjang terukur dalam meter
    val measuredVolume: Float,     // volume terukur dalam m³
    val timestamp: Long,           // waktu pengukuran
    val isValidated: Boolean = false  // status validasi
)
