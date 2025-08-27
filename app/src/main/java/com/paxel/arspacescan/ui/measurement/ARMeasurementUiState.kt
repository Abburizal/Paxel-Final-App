package com.paxel.arspacescan.ui.measurement

import com.google.ar.sceneform.AnchorNode
import com.paxel.arspacescan.R
import com.paxel.arspacescan.data.model.PackageMeasurement

/**
 * UI State untuk AR Measurement Activity
 */
data class ARMeasurementUiState(
    val step: MeasurementStep = MeasurementStep.SELECT_BASE_POINT_1,
    val instructionTextId: Int = R.string.instruction_step_1,
    val corners: List<AnchorNode> = emptyList(),
    val finalResult: PackageMeasurement? = null,
    val isUndoEnabled: Boolean = false,
    val qualityScore: Float = 1.0f,
    val warnings: List<String> = emptyList(),
    val previewHeight: Float = 0f,
    val confidence: Float = 1.0f,
    val timestamp: Long = System.currentTimeMillis(),
    val sessionId: String = generateSessionId()
) {

    /**
     * Helper function untuk mendapatkan progress percentage
     */
    fun getProgressPercentage(): Int {
        return step.getProgressPercentage()
    }

    /**
     * Helper function untuk cek apakah measurement sudah selesai
     */
    fun isCompleted(): Boolean = step == MeasurementStep.COMPLETED

    /**
     * Helper function untuk cek apakah sedang dalam proses define base
     */
    fun isDefiningBase(): Boolean = step.isSelectingBaseCorners()

    /**
     * Helper function untuk cek apakah base sudah terdefinisi
     */
    fun isBaseDefined(): Boolean = step == MeasurementStep.BASE_DEFINED

    /**
     * Helper function untuk mendapatkan jumlah corner yang dibutuhkan untuk step saat ini
     */
    fun getRequiredCornerCount(): Int {
        return step.getExpectedCornerCount()
    }

    /**
     * Helper function untuk validasi state
     */
    fun isValidState(): Boolean {
        return corners.size <= getRequiredCornerCount() &&
                (step != MeasurementStep.COMPLETED || finalResult != null)
    }

    /**
     * Get current corner count
     */
    fun getCornerCount(): Int = corners.size

    /**
     * Check if ready for next step
     */
    fun isReadyForNextStep(): Boolean = getCornerCount() >= getRequiredCornerCount()

    /**
     * Check if can undo current action
     */
    fun canUndo(): Boolean = isUndoEnabled && corners.isNotEmpty() &&
            step != MeasurementStep.SELECT_BASE_POINT_1

    /**
     * Get user-friendly status message
     */
    fun getStatusMessage(): String {
        val progress = "${getCornerCount()}/${getRequiredCornerCount()} titik"
        return if (warnings.isNotEmpty()) "$progress - ${warnings.first()}" else progress
    }

    /**
     * Validation for current measurement state
     */
    fun validate(): ValidationResult {
        val issues = mutableListOf<String>()

        // Check corner count
        if (corners.size != getRequiredCornerCount() && step != MeasurementStep.BASE_DEFINED) {
            issues.add("Incorrect number of corners: expected ${getRequiredCornerCount()}, got ${corners.size}")
        }

        // Check quality score
        if (qualityScore < 0.5f) {
            issues.add("Low measurement quality detected")
        }

        // Check confidence
        if (confidence < 0.7f) {
            issues.add("Low confidence in measurement accuracy")
        }

        return ValidationResult(
            isValid = issues.isEmpty(),
            issues = issues,
            canContinue = issues.isEmpty() || confidence > 0.5f
        )
    }

    /**
     * Get step description
     */
    fun getStepDescription(): String = step.getDescription()

    companion object {
        /**
         * Generate unique session ID
         */
        private fun generateSessionId(): String {
            return "ar_session_${System.currentTimeMillis()}_${(Math.random() * 1000).toInt()}"
        }
    }
}

// ===== SUPPORTING DATA CLASSES =====

/**
 * Validation result for measurement state
 */
data class ValidationResult(
    val isValid: Boolean,
    val issues: List<String> = emptyList(),
    val canContinue: Boolean = isValid,
    val confidence: Float = if (isValid) 1.0f else 0.0f
)

/**
 * Quality metrics for completed measurement
 */
data class QualityMetrics(
    val overallConfidence: Float,
    val estimatedAccuracy: Float,
    val cornerAccuracy: List<Float>,
    val geometryScore: Float,
    val trackingStability: Float,
    val lightingQuality: Float
) {
    /**
     * Get overall quality score (0-1)
     */
    fun getOverallScore(): Float {
        return (overallConfidence + estimatedAccuracy + geometryScore + trackingStability + lightingQuality) / 5f
    }

    /**
     * Get quality grade (A-F)
     */
    fun getQualityGrade(): String {
        return when {
            getOverallScore() >= 0.9f -> "A"
            getOverallScore() >= 0.8f -> "B"
            getOverallScore() >= 0.7f -> "C"
            getOverallScore() >= 0.6f -> "D"
            else -> "F"
        }
    }
}

/**
 * Summary of completed measurement
 */
data class MeasurementSummary(
    val dimensions: String,
    val volume: Float,
    val confidence: Float,
    val accuracy: Float,
    val processingTimeMs: Long
) {
    /**
     * Get formatted summary text
     */
    fun getFormattedText(): String {
        return "Dimensions: $dimensions\n" +
                "Volume: ${String.format("%.3f", volume)} m³\n" +
                "Confidence: ${(confidence * 100).toInt()}%\n" +
                "Accuracy: ±${(accuracy * 100).toInt()}cm\n" +
                "Processing: ${processingTimeMs}ms"
    }
}

// ===== EXTENSION FUNCTIONS =====

/**
 * Extension function to update state with new step
 */
fun ARMeasurementUiState.withNewStep(
    newStep: MeasurementStep,
    newCorners: List<AnchorNode>,
    qualityScore: Float = this.qualityScore,
    warnings: List<String> = emptyList()
): ARMeasurementUiState {
    val newInstructionId = when (newStep) {
        MeasurementStep.SELECT_BASE_POINT_1 -> R.string.instruction_step_1
        MeasurementStep.SELECT_BASE_POINT_2 -> R.string.instruction_step_2
        MeasurementStep.SELECT_BASE_POINT_3 -> R.string.instruction_step_3
        MeasurementStep.SELECT_BASE_POINT_4 -> R.string.instruction_step_4
        MeasurementStep.BASE_DEFINED -> R.string.instruction_set_height
        MeasurementStep.COMPLETED -> R.string.instruction_measurement_complete
    }

    return this.copy(
        step = newStep,
        instructionTextId = newInstructionId,
        corners = newCorners,
        isUndoEnabled = newStep != MeasurementStep.SELECT_BASE_POINT_1 && newCorners.isNotEmpty(),
        qualityScore = qualityScore,
        warnings = warnings,
        timestamp = System.currentTimeMillis()
    )
}

/**
 * Extension function to add warning
 */
fun ARMeasurementUiState.withWarning(warning: String): ARMeasurementUiState {
    return this.copy(
        warnings = this.warnings + warning,
        confidence = (this.confidence * 0.8f).coerceAtLeast(0.1f)
    )
}

/**
 * Extension function to clear warnings
 */
fun ARMeasurementUiState.clearWarnings(): ARMeasurementUiState {
    return this.copy(
        warnings = emptyList(),
        confidence = 1.0f
    )
}