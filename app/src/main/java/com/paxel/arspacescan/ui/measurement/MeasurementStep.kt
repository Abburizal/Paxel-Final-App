package com.paxel.arspacescan.ui.measurement

/**
 * Enum representing the different steps in the AR measurement process
 */
enum class MeasurementStep {
    /** First corner of the base rectangle */
    SELECT_BASE_POINT_1,

    /** Second corner of the base rectangle */
    SELECT_BASE_POINT_2,

    /** Third corner of the base rectangle */
    SELECT_BASE_POINT_3,

    /** Fourth corner of the base rectangle */
    SELECT_BASE_POINT_4,

    /** Base rectangle is complete, now measuring height */
    BASE_DEFINED,

    /** Measurement process is complete */
    COMPLETED;

    /**
     * Get the number of corners expected for this step
     */
    fun getExpectedCornerCount(): Int {
        return when (this) {
            SELECT_BASE_POINT_1 -> 1
            SELECT_BASE_POINT_2 -> 2
            SELECT_BASE_POINT_3 -> 3
            SELECT_BASE_POINT_4 -> 4
            BASE_DEFINED -> 4
            COMPLETED -> 4
        }
    }

    /**
     * Get human-readable description of the step
     */
    fun getDescription(): String {
        return when (this) {
            SELECT_BASE_POINT_1 -> "Pilih titik sudut pertama"
            SELECT_BASE_POINT_2 -> "Pilih titik sudut kedua"
            SELECT_BASE_POINT_3 -> "Pilih titik sudut ketiga"
            SELECT_BASE_POINT_4 -> "Pilih titik sudut keempat"
            BASE_DEFINED -> "Arahkan ke atas paket untuk mengukur tinggi"
            COMPLETED -> "Pengukuran selesai"
        }
    }

    /**
     * Check if this step involves selecting base corners
     */
    fun isSelectingBaseCorners(): Boolean {
        return this in listOf(SELECT_BASE_POINT_1, SELECT_BASE_POINT_2, SELECT_BASE_POINT_3, SELECT_BASE_POINT_4)
    }

    /**
     * Get progress percentage (0-100)
     */
    fun getProgressPercentage(): Int {
        return when (this) {
            SELECT_BASE_POINT_1 -> 10
            SELECT_BASE_POINT_2 -> 25
            SELECT_BASE_POINT_3 -> 40
            SELECT_BASE_POINT_4 -> 55
            BASE_DEFINED -> 80
            COMPLETED -> 100
        }
    }
}