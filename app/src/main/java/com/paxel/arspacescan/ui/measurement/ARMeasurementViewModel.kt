package com.paxel.arspacescan.ui.measurement

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ar.sceneform.AnchorNode
import com.paxel.arspacescan.R
import com.paxel.arspacescan.data.model.MeasurementResult
import com.paxel.arspacescan.util.MeasurementCalculator
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ARMeasurementUiState(
    val step: MeasurementStep = MeasurementStep.START,
    val instructionTextId: Int = R.string.instruction_step_1,
    val points: List<AnchorNode> = emptyList(),
    val corners: List<AnchorNode> = emptyList(),
    val isUndoEnabled: Boolean = false,
    val finalResult: MeasurementResult? = null
)

class ARMeasurementViewModel(private val calculator: MeasurementCalculator) : ViewModel() {

    private val _uiState = MutableStateFlow(ARMeasurementUiState())
    val uiState: StateFlow<ARMeasurementUiState> = _uiState.asStateFlow()

    private val _navigationEvent = MutableSharedFlow<MeasurementResult>()
    val navigationEvent: SharedFlow<MeasurementResult> = _navigationEvent.asSharedFlow()

    fun handleArTap(tappedNode: AnchorNode, context: Context) {
        viewModelScope.launch {
            val currentState = _uiState.value

            when {
                currentState.points.isEmpty() -> {
                    _uiState.value = currentState.copy(
                        points = listOf(tappedNode),
                        instructionTextId = R.string.instruction_step_2,
                        isUndoEnabled = true
                    )
                }
                currentState.points.size == 1 -> {
                    val p1 = currentState.points[0]
                    val p2 = tappedNode
                    val allBasePoints = calculator.calculateBaseCorners(p1, p2)
                    _uiState.value = _uiState.value.copy(
                        step = MeasurementStep.BASE_DEFINED,
                        instructionTextId = R.string.instruction_step_3,
                        points = currentState.points + p2,
                        corners = allBasePoints
                    )
                }
                currentState.step == MeasurementStep.BASE_DEFINED -> {
                    val heightPoint = tappedNode
                    val baseCorners = currentState.corners
                    val result = calculator.calculate3DBox(baseCorners, heightPoint)
                    if (result != null) {
                        _uiState.value = currentState.copy(
                            step = MeasurementStep.COMPLETED,
                            instructionTextId = R.string.instruction_completed,
                            points = currentState.points + heightPoint,
                            corners = result.allCorners,
                            isUndoEnabled = false,
                            finalResult = result.measurement
                        )
                    } else {
                        Toast.makeText(context, "Pengukuran tidak valid, coba lagi.", Toast.LENGTH_SHORT).show()
                        resetMeasurement()
                    }
                }
            }
        }
    }

    fun undoLastPoint() {
        resetMeasurement()
    }

    fun resetMeasurement() {
        _uiState.value.points.forEach { it.anchor?.detach() }
        _uiState.value = ARMeasurementUiState()
    }

    // Method untuk mendapatkan hasil pengukuran yang sudah selesai
    fun getMeasurementResult(): MeasurementResult? {
        return _uiState.value.finalResult
    }
}
