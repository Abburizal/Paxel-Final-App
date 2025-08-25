package com.paxel.arspacescan.ui.measurement

import android.content.Context
import android.util.Log
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.math.Quaternion
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.Color
import com.google.ar.sceneform.rendering.MaterialFactory
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.rendering.ShapeFactory
import com.google.ar.sceneform.ux.ArFragment
import com.paxel.arspacescan.data.model.MeasurementResult

/**
 * Kelas ini bertanggung jawab untuk mengelola semua objek visual (Node)
 * di dalam AR Scene. Tujuannya adalah untuk memisahkan logika visual AR
 * dari ARMeasurementActivity.
 */
class ARSceneManager(private val context: Context, private val arFragment: ArFragment) {

    private var sphereRenderable: ModelRenderable? = null
    private var lineRenderable: ModelRenderable? = null
    private val visualNodes = mutableListOf<Node>()

    init {
        createRenderables()
    }

    private fun createRenderables() {
        MaterialFactory.makeOpaqueWithColor(context, Color(android.graphics.Color.RED))
            .thenAccept { material ->
                sphereRenderable = ShapeFactory.makeSphere(0.01f, Vector3.zero(), material)
            }
            .exceptionally { e ->
                Log.e("ARSceneManager", "Failed to create sphere renderable", e)
                null
            }

        MaterialFactory.makeOpaqueWithColor(context, Color(android.graphics.Color.YELLOW))
            .thenAccept { material ->
                lineRenderable = ShapeFactory.makeCube(
                    Vector3(0.005f, 0.001f, 1f),
                    Vector3.zero(),
                    material
                )
            }
            .exceptionally { e ->
                Log.e("ARSceneManager", "Failed to create line renderable", e)
                null
            }
    }

    /**
     * Membersihkan semua node visual dari scene.
     */
    fun clearScene() {
        visualNodes.forEach {
            it.setParent(null)
        }
        visualNodes.clear()
    }

    /**
     * Menggambar ulang seluruh scene berdasarkan UI state yang baru.
     */
    fun updateScene(state: ARMeasurementUiState) {
        clearScene()

        // Tambahkan bola untuk setiap titik
        state.points.forEach { addSphere(it.worldPosition) }

        when (state.step) {
            MeasurementStep.BASE_DEFINED -> {
                val cornerPositions = state.corners.map { it.worldPosition }
                if (cornerPositions.size >= 4) drawBase(cornerPositions)
            }
            MeasurementStep.COMPLETED -> {
                val cornerPositions = state.corners.map { it.worldPosition }
                if (cornerPositions.size == 8) drawWireframeBox(cornerPositions)
            }
            else -> { /* Biarkan kosong untuk state lain */ }
        }
    }

    fun drawPreview(baseCorners: List<Vector3>, height: Float) {
        // Hapus pratinjau sebelumnya
        visualNodes.filter { it.name == "wireframe_edge_preview" }
            .forEach { it.setParent(null) }
        visualNodes.removeAll { it.name == "wireframe_edge_preview" }

        if (baseCorners.isEmpty()) return

        val pA = baseCorners[0]
        val topCornersPos = baseCorners.map {
            Vector3(it.x, pA.y + height, it.z)
        }
        drawWireframeBox(baseCorners + topCornersPos, isPreview = true)
    }


    private fun addSphere(worldPosition: Vector3) {
        if (sphereRenderable == null) return
        val sphereNode = Node().apply {
            renderable = sphereRenderable
            localPosition = worldPosition
            setParent(arFragment.arSceneView.scene)
        }
        visualNodes.add(sphereNode)
    }

    private fun drawBase(corners: List<Vector3>) {
        if (corners.size < 4) return
        for (i in corners.indices) {
            val start = corners[i]
            val end = corners[(i + 1) % corners.size]
            drawLine(start, end)
        }
    }

    private fun drawWireframeBox(corners: List<Vector3>, isPreview: Boolean = false) {
        if (corners.size < 8) return

        val baseCorners = corners.take(4)
        val topCorners = corners.drop(4)

        // Draw base
        for (i in baseCorners.indices) {
            drawLine(baseCorners[i], baseCorners[(i + 1) % baseCorners.size], isPreview)
        }

        // Draw top
        for (i in topCorners.indices) {
            drawLine(topCorners[i], topCorners[(i + 1) % topCorners.size], isPreview)
        }

        // Draw vertical edges
        for (i in baseCorners.indices) {
            drawLine(baseCorners[i], topCorners[i], isPreview)
        }
    }

    private fun drawLine(start: Vector3, end: Vector3, isPreview: Boolean = false) {
        if (lineRenderable == null) return
        val lineNode = Node().apply {
            renderable = lineRenderable

            val direction = Vector3.subtract(end, start)
            val length = direction.length()
            val center = Vector3.add(start, direction.scaled(0.5f))

            localPosition = center
            localScale = Vector3(1f, 1f, length)
            localRotation = Quaternion.lookRotation(direction.normalized(), Vector3.up())

            setParent(arFragment.arSceneView.scene)

            if (isPreview) {
                name = "wireframe_edge_preview"
            }
        }
        visualNodes.add(lineNode)
    }
}