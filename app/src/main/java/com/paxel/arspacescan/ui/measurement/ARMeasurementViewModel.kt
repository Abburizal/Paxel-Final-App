package com.paxel.arspacescan.ui.measurement

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ar.sceneform.AnchorNode
import com.paxel.arspacescan.R
import com.paxel.arspacescan.data.model.MeasurementResult
import com.paxel.arspacescan.data.model.PackageMeasurement
import com.paxel.arspacescan.data.repository.MeasurementRepository
import com.paxel.arspacescan.util.MeasurementCalculator
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

class ARMeasurementViewModel(private val repository: MeasurementRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(ARMeasurementUiState())
    val uiState = _uiState.asStateFlow()

    private val _navigationEvent = MutableSharedFlow<MeasurementResult>()
    val navigationEvent = _navigationEvent.asSharedFlow()

    fun handleArTap(anchorNode: AnchorNode, context: Context) {
        val currentState = _uiState.value
        when (currentState.step) {
            MeasurementStep.START -> {
                addPoint(anchorNode)
                _uiState.update {
                    it.copy(
                        step = MeasurementStep.BASE_POINT_B_ADDED,
                        instructionTextId = R.string.instruction_step_2
                    )
                }
            }
            MeasurementStep.BASE_POINT_B_ADDED -> {
                addPoint(anchorNode)
                defineBaseCorners(context)
            }
            MeasurementStep.BASE_DEFINED -> {
                defineHeightAndComplete(anchorNode)
            }
            else -> {
                // Do nothing in other states like COMPLETED
            }
        }
    }

    private fun addPoint(point: AnchorNode) {
        _uiState.update {
            it.copy(
                points = it.points + point,
                isUndoEnabled = true
            )
        }
    }

    // [FINAL] Logika yang disempurnakan untuk akurasi yang lebih tinggi
    private fun defineBaseCorners(context: Context) {
        val points = _uiState.value.points
        if (points.size < 2) return

        val p1 = points[0].worldPosition
        val p2 = points[1].worldPosition

        // 1. Ratakan ketinggian (sumbu Y) untuk memastikan alasnya datar
        val avgY = (p1.y + p2.y) / 2f

        // 2. Tentukan titik minimum dan maksimum untuk membentuk Bounding Box 2D (pada bidang XZ)
        val minX = min(p1.x, p2.x)
        val maxX = max(p1.x, p2.x)
        val minZ = min(p1.z, p2.z)
        val maxZ = max(p1.z, p2.z)

        // 3. Buat 4 sudut kotak yang sempurna secara matematis (ortogonal)
        val pA = com.google.ar.sceneform.math.Vector3(minX, avgY, minZ)
        val pB = com.google.ar.sceneform.math.Vector3(minX, avgY, maxZ)
        val pC = com.google.ar.sceneform.math.Vector3(maxX, avgY, maxZ)
        val pD = com.google.ar.sceneform.math.Vector3(maxX, avgY, minZ)

        // Urutan sudut disesuaikan agar kalkulasi di MeasurementCalculator tetap benar
        val corners = listOf(pA, pB, pC, pD).map { position ->
            AnchorNode().apply { worldPosition = position }
        }

        _uiState.update {
            it.copy(
                corners = corners,
                step = MeasurementStep.BASE_DEFINED,
                instructionTextId = R.string.instruction_step_3
            )
        }
    }


    private fun defineHeightAndComplete(heightPoint: AnchorNode) {
        val baseCorners = _uiState.value.corners
        if (baseCorners.isEmpty()) return

        // Logika ini sudah akurat karena hanya mengambil nilai Y dari titik tinggi
        val height = kotlin.math.abs(heightPoint.worldPosition.y - baseCorners[0].worldPosition.y)

        val topCorners = baseCorners.map { baseNode ->
            val pos = baseNode.worldPosition
            AnchorNode().apply {
                worldPosition = com.google.ar.sceneform.math.Vector3(pos.x, pos.y + height, pos.z)
            }
        }

        val allCorners = baseCorners + topCorners
        val result = MeasurementCalculator.calculateFinalMeasurement(allCorners)

        if (result != null) {
            _uiState.update {
                it.copy(
                    corners = allCorners,
                    step = MeasurementStep.COMPLETED,
                    instructionTextId = R.string.instruction_completed,
                    isUndoEnabled = false,
                    finalResult = result
                )
            }
        }
    }


    fun undoLastPoint() {
        val currentState = _uiState.value
        if (currentState.points.isNotEmpty()) {
            val newPoints = currentState.points.dropLast(1)
            val newStep = if (newPoints.isEmpty()) MeasurementStep.START else MeasurementStep.BASE_POINT_B_ADDED
            val newInstruction = if (newPoints.isEmpty()) R.string.instruction_step_1 else R.string.instruction_step_2

            _uiState.update {
                it.copy(
                    points = newPoints,
                    corners = emptyList(),
                    step = newStep,
                    instructionTextId = newInstruction,
                    isUndoEnabled = newPoints.isNotEmpty()
                )
            }
        }
    }

    fun reset() {
        _uiState.value = ARMeasurementUiState()
    }

    fun completeMeasurement() {
        viewModelScope.launch {
            _uiState.value.finalResult?.let {
                _navigationEvent.emit(it)
            }
        }
    }

    fun updateHeight(height: Float) {
        // Fungsi ini bisa digunakan nanti jika diperlukan
    }

    // Tambahkan fungsi delete
    fun delete(measurement: PackageMeasurement) {
        viewModelScope.launch {
            repository.delete(measurement)
        }
    }
}

data class ARMeasurementUiState(
    val step: MeasurementStep = MeasurementStep.START,
    val instructionTextId: Int = R.string.instruction_step_1,
    val points: List<AnchorNode> = emptyList(),
    val corners: List<AnchorNode> = emptyList(),
    val isUndoEnabled: Boolean = false,
    val finalResult: MeasurementResult? = null
)