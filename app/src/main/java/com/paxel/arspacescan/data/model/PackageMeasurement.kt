package com.paxel.arspacescan.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "package_measurements")
data class PackageMeasurement(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "package_name")
    val packageName: String,

    @ColumnInfo(name = "length_cm")
    val length: Float,

    @ColumnInfo(name = "width_cm")
    val width: Float,

    @ColumnInfo(name = "height_cm")
    val height: Float,

    @ColumnInfo(name = "volume_cm3")
    val volume: Float,

    @ColumnInfo(name = "volumetric_weight_kg")
    val volumetricWeight: Float,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "screenshot_path")
    val screenshotPath: String? = null
)