package com.paxel.arspacescan.ui.measurement

/**
 * Represents the different steps in the AR measurement process
 */
enum class MeasurementStep {
    /**
     * Initial state - waiting for first measurement point
     */
    START,

    /**
     * [PERBAIKAN] First base point 'A' has been added, waiting for point 'B' (diagonal)
     */
    BASE_POINT_B_ADDED,

    /**
     * Base rectangle has been defined with 4 corners
     */
    BASE_DEFINED,

    /**
     * Box measurement is completed with all 8 corners
     */
    COMPLETED
}