package com.paxel.arspacescan.ui.measurement

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.math.Vector3
import com.paxel.arspacescan.R
import com.paxel.arspacescan.data.model.PackageMeasurement
import com.paxel.arspacescan.util.AngleValidator
import com.paxel.arspacescan.util.MeasurementCalculator
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.max

class ARMeasurementViewModel : ViewModel() {

    companion object {
        private const val TAG = "ARMeasurementViewModel"
    }

    private val _uiState = MutableStateFlow(ARMeasurementUiState())
    val uiState = _uiState.asStateFlow()

    private val _navigationEvent = MutableSharedFlow<PackageMeasurement>()
    val navigationEvent = _navigationEvent.asSharedFlow()

    private val _warningMessage = MutableStateFlow<String?>(null)
    val warningMessage = _warningMessage.asSharedFlow()

    // Variabel untuk menyimpan nama paket
    private var packageName: String = "Paket"

    // Fungsi untuk mengatur nama paket dari Activity
    fun setPackageName(name: String) {
        packageName = name
    }

    fun handleArTap(anchorNode: AnchorNode, context: android.content.Context) {
        try {
            val currentState = _uiState.value
            Log.d(TAG, "Handling AR tap for step: ${currentState.step}")

            when (currentState.step) {
                MeasurementStep.SELECT_BASE_POINT_1,
                MeasurementStep.SELECT_BASE_POINT_2,
                MeasurementStep.SELECT_BASE_POINT_3,
                MeasurementStep.SELECT_BASE_POINT_4 -> {
                    defineBase(anchorNode.worldPosition)
                }

                MeasurementStep.BASE_DEFINED -> {
                    val baseLevelY = currentState.corners.firstOrNull()?.worldPosition?.y ?: 0f
                    val measuredHeight = max(0.01f, anchorNode.worldPosition.y - baseLevelY)
                    confirmHeight(measuredHeight)
                }

                MeasurementStep.COMPLETED -> {
                    // No action after completion
                    Log.d(TAG, "Measurement already completed, ignoring tap")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling AR tap", e)
            showWarningMessage("Gagal memproses tap AR: ${e.message}")
        }
    }

    private fun defineBase(position: Vector3) {
        try {
            _uiState.update { currentState ->
                val newCorners = currentState.corners.toMutableList()
                newCorners.add(AnchorNode().apply { worldPosition = position })

                val newStep = when (newCorners.size) {
                    1 -> MeasurementStep.SELECT_BASE_POINT_2
                    2 -> MeasurementStep.SELECT_BASE_POINT_3
                    3 -> MeasurementStep.SELECT_BASE_POINT_4
                    4 -> MeasurementStep.BASE_DEFINED
                    else -> currentState.step
                }

                val newInstruction = when (newStep) {
                    MeasurementStep.SELECT_BASE_POINT_2 -> R.string.instruction_step_2
                    MeasurementStep.SELECT_BASE_POINT_3 -> R.string.instruction_step_3
                    MeasurementStep.SELECT_BASE_POINT_4 -> R.string.instruction_step_4
                    MeasurementStep.BASE_DEFINED -> R.string.instruction_set_height
                    else -> currentState.instructionTextId
                }

                // Validate base shape if we have at least 3 corners
                if (newCorners.size >= 3) {
                    validateBaseShape(newCorners.map { it.worldPosition })
                }

                Log.d(TAG, "Base definition step completed: ${newCorners.size} corners, step: $newStep")

                currentState.copy(
                    corners = newCorners,
                    step = newStep,
                    instructionTextId = newInstruction,
                    isUndoEnabled = true
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error defining base", e)
            showWarningMessage("Gagal menambah titik base: ${e.message}")
        }
    }

    private fun confirmHeight(height: Float) {
        try {
            val currentState = _uiState.value
            if (currentState.step != MeasurementStep.BASE_DEFINED || currentState.corners.size != 4) {
                Log.w(TAG, "Cannot confirm height: invalid state")
                return
            }

            Log.d(TAG, "Confirming height: $height meters")
            completeMeasurement(currentState.corners, height)
        } catch (e: Exception) {
            Log.e(TAG, "Error confirming height", e)
            showWarningMessage("Gagal mengkonfirmasi tinggi: ${e.message}")
        }
    }

    private fun completeMeasurement(baseCorners: List<AnchorNode>, height: Float) {
        try {
            // VALIDASI: Nama paket harus tidak kosong
            if (packageName.isBlank()) {
                showWarningMessage("Nama paket tidak valid. Silakan ulangi pengukuran.")
                Log.w(TAG, "Measurement aborted: package name is blank")
                return
            }
            Log.d(TAG, "Completing measurement with ${baseCorners.size} corners and height $height")
            val result = MeasurementCalculator.calculate(baseCorners.map { it.worldPosition }, height)
            if (!result.isValid()) {
                Log.w(TAG, "Invalid measurement result: $result")
                showWarningMessage("Hasil pengukuran tidak valid, coba lagi")
                return
            }
            _uiState.update {
                it.copy(
                    step = MeasurementStep.COMPLETED,
                    instructionTextId = R.string.instruction_measurement_complete,
                    finalResult = PackageMeasurement(
                        packageName = this.packageName,
                        declaredSize = "",
                        width = result.width,
                        height = result.height,
                        depth = result.depth,
                        volume = result.volume,
                        timestamp = System.currentTimeMillis()
                    ),
                    isUndoEnabled = false,
                    qualityScore = result.getConfidence()
                )
            }
            clearWarningMessage()
            Log.d(TAG, "Measurement completed successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Error completing measurement", e)
            showWarningMessage("Gagal menyelesaikan pengukuran: ${e.message}")
        }
    }

    fun undoLastPoint() {
        try {
            _uiState.update { currentState ->
                if (currentState.corners.isEmpty()) return@update currentState

                // ✅ FIXED: Compatible with API 24+ (instead of removeLast())
                val newCorners = currentState.corners.toMutableList()
                if (newCorners.isNotEmpty()) {
                    newCorners.removeAt(newCorners.size - 1)
                }

                val newStep = when (newCorners.size) {
                    0 -> MeasurementStep.SELECT_BASE_POINT_1
                    1 -> MeasurementStep.SELECT_BASE_POINT_2
                    2 -> MeasurementStep.SELECT_BASE_POINT_3
                    3 -> MeasurementStep.SELECT_BASE_POINT_4
                    else -> currentState.step
                }

                val newInstruction = when (newStep) {
                    MeasurementStep.SELECT_BASE_POINT_1 -> R.string.instruction_step_1
                    MeasurementStep.SELECT_BASE_POINT_2 -> R.string.instruction_step_2
                    MeasurementStep.SELECT_BASE_POINT_3 -> R.string.instruction_step_3
                    MeasurementStep.SELECT_BASE_POINT_4 -> R.string.instruction_step_4
                    else -> currentState.instructionTextId
                }

                clearWarningMessage()
                Log.d(TAG, "Undo completed: ${newCorners.size} corners remaining")

                currentState.copy(
                    corners = newCorners,
                    step = newStep,
                    instructionTextId = newInstruction,
                    isUndoEnabled = newCorners.isNotEmpty(),
                    finalResult = null
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during undo", e)
            showWarningMessage("Gagal membatalkan aksi terakhir")
        }
    }

    fun reset() {
        try {
            _uiState.value = ARMeasurementUiState()
            clearWarningMessage()
            Log.d(TAG, "Measurement reset completed")
        } catch (e: Exception) {
            Log.e(TAG, "Error during reset", e)
        }
    }

    private fun validateBaseShape(corners: List<Vector3>) {
        try {
            val validationResult = AngleValidator.validateBaseAngles(corners)
            if (!validationResult.isValid) {
                val badAngles = validationResult.problematicAngles.joinToString("°, ") { "%.0f".format(it) }
                val warningMessage = "Perhatian: Sudut alas tidak presisi (sekitar ${badAngles}°). Hasil mungkin kurang akurat."
                showWarningMessage(warningMessage)
            } else {
                clearWarningMessage()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error validating base shape", e)
        }
    }

    private fun showWarningMessage(message: String) {
        _warningMessage.value = message
        Log.w(TAG, "Warning: $message")
    }

    private fun clearWarningMessage() {
        _warningMessage.value = null
    }

    fun navigateToResult() {
        viewModelScope.launch {
            try {
                _uiState.value.finalResult?.let {
                    _navigationEvent.emit(it)
                    Log.d(TAG, "Navigation to result emitted")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error navigating to result", e)
            }
        }
    }

    /**
     * Get current measurement progress (0-100)
     */
    fun getCurrentProgress(): Int {
        return _uiState.value.getProgressPercentage()
    }

    /**
     * Check if measurement can be completed
     */
    fun canCompleteMeasurement(): Boolean {
        val state = _uiState.value
        return state.step == MeasurementStep.COMPLETED && state.finalResult != null
    }

    /**
     * Get measurement quality assessment
     */
    fun getMeasurementQuality(): String {
        val qualityScore = _uiState.value.qualityScore
        return when {
            qualityScore >= 0.9f -> "Sangat Baik"
            qualityScore >= 0.7f -> "Baik"
            qualityScore >= 0.5f -> "Cukup"
            else -> "Rendah"
        }
    }
}