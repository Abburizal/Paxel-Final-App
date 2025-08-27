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

/**
 * ARSceneManager dengan improved rendering untuk box 3D preview
 */
class ARSceneManager(private val context: Context, private val arFragment: ArFragment) {

    private var sphereRenderable: ModelRenderable? = null
    private var lineRenderable: ModelRenderable? = null
    private var previewLineRenderable: ModelRenderable? = null
    private val visualNodes = mutableListOf<Node>()

    // Flag untuk tracking readiness renderables
    private var isRenderablesReady = false
    private val pendingDrawRequests = mutableListOf<() -> Unit>()

    companion object {
        private const val TAG = "ARSceneManager"
        private const val SPHERE_RADIUS = 0.01f
        private const val LINE_WIDTH = 0.005f
        private const val LINE_HEIGHT = 0.001f
        private const val PREVIEW_ALPHA = 0.7f
    }

    init {
        createRenderables()
    }

    private fun createRenderables() {
        var completedCount = 0
        val totalRenderables = 3

        fun checkAllReady() {
            if (completedCount == totalRenderables) {
                isRenderablesReady = true
                Log.d(TAG, "All renderables ready, processing ${pendingDrawRequests.size} pending requests")
                // Process pending draw requests
                pendingDrawRequests.forEach {
                    try {
                        it.invoke()
                    } catch (e: Exception) {
                        Log.w(TAG, "Error processing pending draw request", e)
                    }
                }
                pendingDrawRequests.clear()
            }
        }

        // Create sphere renderable (red color for corner points)
        try {
            MaterialFactory.makeOpaqueWithColor(context, Color(android.graphics.Color.RED))
                .thenAccept { material ->
                    try {
                        sphereRenderable = ShapeFactory.makeSphere(SPHERE_RADIUS, Vector3.zero(), material)
                        completedCount++
                        Log.d(TAG, "Sphere renderable created successfully ($completedCount/$totalRenderables)")
                        checkAllReady()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error creating sphere shape", e)
                        completedCount++
                        checkAllReady()
                    }
                }
                .exceptionally { e ->
                    Log.e(TAG, "Failed to create sphere renderable", e)
                    completedCount++
                    checkAllReady()
                    null
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing sphere material", e)
            completedCount++
            checkAllReady()
        }

        // Create line renderable (yellow color for measurement lines)
        try {
            MaterialFactory.makeOpaqueWithColor(context, Color(android.graphics.Color.YELLOW))
                .thenAccept { material ->
                    try {
                        lineRenderable = ShapeFactory.makeCube(
                            Vector3(LINE_WIDTH, LINE_HEIGHT, 1f),
                            Vector3.zero(),
                            material
                        )
                        completedCount++
                        Log.d(TAG, "Line renderable created successfully ($completedCount/$totalRenderables)")
                        checkAllReady()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error creating line shape", e)
                        completedCount++
                        checkAllReady()
                    }
                }
                .exceptionally { e ->
                    Log.e(TAG, "Failed to create line renderable", e)
                    completedCount++
                    checkAllReady()
                    null
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing line material", e)
            completedCount++
            checkAllReady()
        }

        // Create preview line renderable (blue with transparency)
        try {
            MaterialFactory.makeOpaqueWithColor(
                context,
                Color(android.graphics.Color.argb((255 * PREVIEW_ALPHA).toInt(), 0, 200, 255))
            )
                .thenAccept { material ->
                    try {
                        previewLineRenderable = ShapeFactory.makeCube(
                            Vector3(LINE_WIDTH * 1.5f, LINE_HEIGHT * 1.5f, 1f),
                            Vector3.zero(),
                            material
                        )
                        completedCount++
                        Log.d(TAG, "Preview line renderable created successfully ($completedCount/$totalRenderables)")
                        checkAllReady()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error creating preview line shape", e)
                        completedCount++
                        checkAllReady()
                    }
                }
                .exceptionally { e ->
                    Log.e(TAG, "Failed to create preview line renderable", e)
                    completedCount++
                    checkAllReady()
                    null
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing preview line material", e)
            completedCount++
            checkAllReady()
        }
    }

    /**
     * Improved drawPreview dengan better validation dan logging
     */
    fun drawPreview(baseCorners: List<Vector3>, height: Float) {
        // Queue request jika renderables belum ready
        if (!isRenderablesReady) {
            Log.d(TAG, "Renderables not ready, queueing preview draw request")
            pendingDrawRequests.add { drawPreview(baseCorners, height) }
            return
        }

        try {
            // Hapus preview sebelumnya
            removePreviewElements()

            // Better validation dengan logging
            if (baseCorners.isEmpty()) {
                Log.d(TAG, "No base corners provided, skipping preview")
                return
            }

            if (height <= 0f) {
                Log.d(TAG, "Invalid height: $height, skipping preview")
                return
            }

            Log.d(TAG, "Drawing preview with ${baseCorners.size} corners, height: $height")

            val wireframeCorners = generateWireframeCorners(baseCorners, height)

            if (wireframeCorners.size < 8) {
                Log.w(TAG, "Generated only ${wireframeCorners.size} wireframe corners, need 8")
                return
            }

            drawWireframeBox(wireframeCorners, isPreview = true)
            Log.d(TAG, "Preview drawn successfully with ${wireframeCorners.size} corners")

        } catch (e: Exception) {
            Log.e(TAG, "Error drawing preview", e)
        }
    }

    /**
     * Improved wireframe box generation dengan validation
     */
    private fun generateWireframeCorners(baseCorners: List<Vector3>, height: Float): List<Vector3> {
        if (baseCorners.size < 4) {
            Log.w(TAG, "Not enough base corners: ${baseCorners.size}, need 4")
            return emptyList()
        }

        return try {
            val baseY = baseCorners[0].y
            Log.d(TAG, "Generating wireframe: baseY=$baseY, height=$height")

            // Ensure corners are in correct order (clockwise or counter-clockwise)
            val orderedCorners = ensureCornerOrder(baseCorners)

            val topCorners = orderedCorners.map { corner ->
                Vector3(corner.x, baseY + height, corner.z)
            }

            val result = orderedCorners + topCorners // 4 base + 4 top = 8 corners
            Log.d(TAG, "Generated ${result.size} wireframe corners")

            result
        } catch (e: Exception) {
            Log.e(TAG, "Error generating wireframe corners", e)
            emptyList()
        }
    }

    /**
     * Ensure proper corner ordering untuk consistent wireframe
     */
    private fun ensureCornerOrder(corners: List<Vector3>): List<Vector3> {
        if (corners.size != 4) return corners

        return try {
            // Simple ordering by calculating centroid and sorting by angle
            val centroid = Vector3(
                corners.sumOf { it.x.toDouble() }.toFloat() / 4,
                corners.sumOf { it.y.toDouble() }.toFloat() / 4,
                corners.sumOf { it.z.toDouble() }.toFloat() / 4
            )

            corners.sortedBy { corner ->
                Math.atan2((corner.z - centroid.z).toDouble(), (corner.x - centroid.x).toDouble())
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error ordering corners", e)
            corners
        }
    }

    /**
     * Enhanced wireframe drawing dengan better error handling
     */
    private fun drawWireframeBox(corners: List<Vector3>, isPreview: Boolean = false) {
        if (corners.size < 8) {
            Log.w(TAG, "Not enough corners for wireframe box: ${corners.size}")
            return
        }

        try {
            val baseCorners = corners.take(4)
            val topCorners = corners.drop(4)

            Log.d(TAG, "Drawing wireframe box: ${baseCorners.size} base + ${topCorners.size} top corners")

            var edgesDrawn = 0

            // Draw base edges
            for (i in baseCorners.indices) {
                val success = drawLine(baseCorners[i], baseCorners[(i + 1) % baseCorners.size], isPreview)
                if (success) edgesDrawn++
            }

            // Draw top edges
            for (i in topCorners.indices) {
                val success = drawLine(topCorners[i], topCorners[(i + 1) % topCorners.size], isPreview)
                if (success) edgesDrawn++
            }

            // Draw vertical edges
            for (i in baseCorners.indices) {
                val success = drawLine(baseCorners[i], topCorners[i], isPreview)
                if (success) edgesDrawn++
            }

            Log.d(TAG, "Wireframe box drawn: $edgesDrawn edges successfully created")

        } catch (e: Exception) {
            Log.e(TAG, "Error drawing wireframe box", e)
        }
    }

    /**
     * Enhanced line drawing dengan return success status
     */
    private fun drawLine(start: Vector3, end: Vector3, isPreview: Boolean = false): Boolean {
        val renderable = if (isPreview) previewLineRenderable else lineRenderable

        if (renderable == null) {
            Log.w(TAG, "${if (isPreview) "Preview" else "Regular"} line renderable not ready")
            return false
        }

        return try {
            val direction = Vector3.subtract(end, start)
            val length = direction.length()

            if (length < 0.001f) { // Threshold untuk menghindari zero-length lines
                Log.w(TAG, "Line too short to draw: $length")
                return false
            }

            val center = Vector3.add(start, direction.scaled(0.5f))
            val normalizedDirection = direction.normalized()

            val lineNode = Node().apply {
                this.renderable = renderable
                localPosition = center
                localScale = Vector3(1f, 1f, length)
                localRotation = Quaternion.lookRotation(normalizedDirection, Vector3.up())
                setParent(arFragment.arSceneView.scene)

                if (isPreview) {
                    name = "wireframe_edge_preview"
                }
            }

            visualNodes.add(lineNode)
            true

        } catch (e: Exception) {
            Log.e(TAG, "Error drawing line", e)
            false
        }
    }

    /**
     * Improved preview element removal
     */
    private fun removePreviewElements() {
        try {
            val previewNodes = visualNodes.filter { it.name == "wireframe_edge_preview" }
            Log.d(TAG, "Removing ${previewNodes.size} preview elements")

            previewNodes.forEach { node ->
                try {
                    node.setParent(null)
                } catch (e: Exception) {
                    Log.w(TAG, "Error removing preview node", e)
                }
            }
            visualNodes.removeAll(previewNodes)

        } catch (e: Exception) {
            Log.e(TAG, "Error removing preview elements", e)
        }
    }

    /**
     * Membersihkan semua node visual dari scene.
     */
    fun clearScene() {
        try {
            Log.d(TAG, "Clearing scene: ${visualNodes.size} nodes")
            visualNodes.forEach { node ->
                try {
                    node.setParent(null)
                } catch (e: Exception) {
                    Log.w(TAG, "Error removing node from scene", e)
                }
            }
            visualNodes.clear()
            Log.d(TAG, "Scene cleared successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing scene", e)
        }
    }

    /**
     * Menggambar ulang seluruh scene berdasarkan UI state yang baru.
     */
    fun updateScene(state: ARMeasurementUiState) {
        try {
            clearScene()

            state.corners.forEach { cornerNode ->
                addSphere(cornerNode.worldPosition)
            }

            when (state.step) {
                MeasurementStep.SELECT_BASE_POINT_1,
                MeasurementStep.SELECT_BASE_POINT_2,
                MeasurementStep.SELECT_BASE_POINT_3 -> {
                    // Just show points, no lines yet
                }

                MeasurementStep.SELECT_BASE_POINT_4 -> {
                    // Show partial base outline
                    val cornerPositions = state.corners.map { it.worldPosition }
                    if (cornerPositions.size >= 2) {
                        drawPartialBase(cornerPositions)
                    }
                }

                MeasurementStep.BASE_DEFINED -> {
                    // Show complete base rectangle
                    val cornerPositions = state.corners.map { it.worldPosition }
                    if (cornerPositions.size >= 4) {
                        drawBase(cornerPositions)
                    }
                }

                MeasurementStep.COMPLETED -> {
                    // Generate 8 corners untuk wireframe box
                    val cornerPositions = state.corners.map { it.worldPosition }
                    if (cornerPositions.size >= 4 && state.finalResult != null) {
                        val height = state.finalResult.height
                        val wireframeCorners = generateWireframeCorners(cornerPositions, height)
                        drawWireframeBox(wireframeCorners, isPreview = false)
                    }
                }
            }

            Log.d(TAG, "Scene updated for step: ${state.step}")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating scene", e)
        }
    }

    /**
     * Tambahkan sphere di posisi tertentu
     */
    private fun addSphere(worldPosition: Vector3) {
        if (sphereRenderable == null) {
            Log.w(TAG, "Sphere renderable not ready")
            return
        }

        try {
            val sphereNode = Node().apply {
                renderable = sphereRenderable
                localPosition = worldPosition
                setParent(arFragment.arSceneView.scene)
            }
            visualNodes.add(sphereNode)
        } catch (e: Exception) {
            Log.e(TAG, "Error adding sphere", e)
        }
    }

    /**
     * Gambar garis parsial untuk base yang belum selesai
     */
    private fun drawPartialBase(corners: List<Vector3>) {
        try {
            for (i in 0 until corners.size - 1) {
                drawLine(corners[i], corners[i + 1], isPreview = false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error drawing partial base", e)
        }
    }

    /**
     * Gambar base rectangle lengkap
     */
    private fun drawBase(corners: List<Vector3>) {
        if (corners.size < 4) return

        try {
            for (i in corners.indices) {
                val start = corners[i]
                val end = corners[(i + 1) % corners.size]
                drawLine(start, end, isPreview = false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error drawing base", e)
        }
    }

    /**
     * Cleanup resources saat manager tidak digunakan lagi
     */
    fun cleanup() {
        try {
            clearScene()
            sphereRenderable = null
            lineRenderable = null
            previewLineRenderable = null
            pendingDrawRequests.clear()
            isRenderablesReady = false
            Log.d(TAG, "ARSceneManager cleaned up")
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }
}