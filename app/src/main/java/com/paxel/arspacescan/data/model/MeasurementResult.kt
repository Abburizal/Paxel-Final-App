package com.paxel.arspacescan.data.model
import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(tableName = "measurement_results")
data class MeasurementResult(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val length: Float,
    val width: Float,
    val height: Float,
    val volume: Float

) : Parcelable