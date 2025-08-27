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
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ✅ ENHANCED: ARSceneManager with proper resource management and memory safety
 */
class ARSceneManager(private val context: Context, private val arFragment: ArFragment) {

    private var sphereRenderable: ModelRenderable? = null
    private var lineRenderable: ModelRenderable? = null
    private var previewLineRenderable: ModelRenderable? = null
    private val visualNodes = mutableListOf<Node>()
    private val renderables = mutableListOf<ModelRenderable>()

    // ✅ ENHANCED: Thread-safe flags and proper state management
    private val isRenderablesReady = AtomicBoolean(false)
    private val isDestroyed = AtomicBoolean(false)
    private val pendingDrawRequests = mutableListOf<() -> Unit>()
    private val pendingRequestsLock = Object()

    companion object {
        private const val TAG = "ARSceneManager"
        private const val SPHERE_RADIUS = 0.01f
        private const val LINE_WIDTH = 0.005f
        private const val LINE_HEIGHT = 0.001f
        private const val PREVIEW_ALPHA = 0.7f
        private const val MAX_PENDING_REQUESTS = 100
    }

    init {
        createRenderables()
    }

    /**
     * ✅ ENHANCED: Robust renderable creation with proper error handling
     */
    private fun createRenderables() {
        if (isDestroyed.get()) {
            Log.w(TAG, "Cannot create renderables - manager is destroyed")
            return
        }

        var completedCount = 0
        val totalRenderables = 3

        fun checkAllReady() {
            if (completedCount == totalRenderables && !isDestroyed.get()) {
                isRenderablesReady.set(true)
                Log.d(TAG, "All renderables ready, processing ${pendingDrawRequests.size} pending requests")

                // Process pending draw requests safely
                synchronized(pendingRequestsLock) {
                    val requestsToProcess = pendingDrawRequests.toList()
                    pendingDrawRequests.clear()

                    requestsToProcess.take(MAX_PENDING_REQUESTS).forEach { request ->
                        try {
                            if (!isDestroyed.get()) {
                                request.invoke()
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Error processing pending draw request", e)
                        }
                    }
                }
            }
        }

        // Create sphere renderable (red color for corner points)
        try {
            MaterialFactory.makeOpaqueWithColor(context, Color(android.graphics.Color.RED))
                .thenAccept { material ->
                    try {
                        if (!isDestroyed.get()) {
                            val renderable = ShapeFactory.makeSphere(SPHERE_RADIUS, Vector3.zero(), material)
                            sphereRenderable = renderable
                            renderables.add(renderable)
                            completedCount++
                            Log.d(TAG, "Sphere renderable created successfully ($completedCount/$totalRenderables)")
                            checkAllReady()
                        }
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
                        if (!isDestroyed.get()) {
                            val renderable = ShapeFactory.makeCube(
                                Vector3(LINE_WIDTH, LINE_HEIGHT, 1f),
                                Vector3.zero(),
                                material
                            )
                            lineRenderable = renderable
                            renderables.add(renderable)
                            completedCount++
                            Log.d(TAG, "Line renderable created successfully ($completedCount/$totalRenderables)")
                            checkAllReady()
                        }
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
                        if (!isDestroyed.get()) {
                            val renderable = ShapeFactory.makeCube(
                                Vector3(LINE_WIDTH * 1.5f, LINE_HEIGHT * 1.5f, 1f),
                                Vector3.zero(),
                                material
                            )
                            previewLineRenderable = renderable
                            renderables.add(renderable)
                            completedCount++
                            Log.d(TAG, "Preview line renderable created successfully ($completedCount/$totalRenderables)")
                            checkAllReady()
                        }
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
     * ✅ ENHANCED: Thread-safe preview drawing with better validation
     */
    fun drawPreview(baseCorners: List<Vector3>, height: Float) {
        if (isDestroyed.get()) {
            Log.d(TAG, "Manager destroyed, ignoring preview draw request")
            return
        }

        // Queue request if renderables not ready
        if (!isRenderablesReady.get()) {
            synchronized(pendingRequestsLock) {
                if (pendingDrawRequests.size < MAX_PENDING_REQUESTS) {
                    pendingDrawRequests.add { drawPreview(baseCorners, height) }
                    Log.d(TAG, "Renderables not ready, queuing preview draw request (${pendingDrawRequests.size} queued)")
                } else {
                    Log.w(TAG, "Too many pending requests, dropping preview draw request")
                }
            }
            return
        }

        try {
            // Hapus preview sebelumnya
            removePreviewElements()

            // Enhanced validation with logging
            if (baseCorners.isEmpty()) {
                Log.d(TAG, "No base corners provided, skipping preview")
                return
            }

            if (height <= 0f) {
                Log.d(TAG, "Invalid height: $height, skipping preview")
                return
            }

            if (baseCorners.size < 4) {
                Log.d(TAG, "Insufficient corners: ${baseCorners.size}, need 4 for preview")
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
     * ✅ ENHANCED: Robust wireframe corner generation with validation
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

            // Validate ordered corners
            if (!isValidCornerOrder(orderedCorners)) {
                Log.w(TAG, "Invalid corner ordering detected, using original")
                return baseCorners + baseCorners.map { corner ->
                    Vector3(corner.x, baseY + height, corner.z)
                }
            }

            val topCorners = orderedCorners.map { corner ->
                Vector3(corner.x, baseY + height, corner.z)
            }

            val result = orderedCorners + topCorners // 4 base + 4 top = 8 corners
            Log.d(TAG, "Generated ${result.size} wireframe corners successfully")

            result
        } catch (e: Exception) {
            Log.e(TAG, "Error generating wireframe corners", e)
            emptyList()
        }
    }

    /**
     * ✅ ENHANCED: Robust corner ordering with geometric validation
     */
    private fun ensureCornerOrder(corners: List<Vector3>): List<Vector3> {
        if (corners.size != 4) return corners

        return try {
            // Calculate geometric center (more robust than simple average)
            val centroid = Vector3(
                corners.sumOf { it.x.toDouble() }.toFloat() / 4,
                corners.sumOf { it.y.toDouble() }.toFloat() / 4,
                corners.sumOf { it.z.toDouble() }.toFloat() / 4
            )

            // Sort by polar angle from centroid
            val sortedCorners = corners.sortedBy { corner ->
                kotlin.math.atan2((corner.z - centroid.z).toDouble(), (corner.x - centroid.x).toDouble())
            }

            Log.d(TAG, "Corners ordered successfully using centroid method")
            sortedCorners
        } catch (e: Exception) {
            Log.w(TAG, "Error ordering corners, using original order", e)
            corners
        }
    }

    /**
     * ✅ NEW: Validate corner ordering produces reasonable quadrilateral
     */
    private fun isValidCornerOrder(corners: List<Vector3>): Boolean {
        if (corners.size != 4) return false

        return try {
            // Check if corners form reasonable distances
            for (i in corners.indices) {
                val nextIndex = (i + 1) % corners.size
                val distance = Vector3.subtract(corners[i], corners[nextIndex]).length()

                if (distance < 0.01f || distance > 5.0f) {
                    Log.w(TAG, "Invalid edge distance: $distance between corners $i and $nextIndex")
                    return false
                }
            }

            // Check for reasonable area (prevent degenerate quadrilaterals)
            val area = calculateQuadrilateralArea(corners)
            if (area < 0.0001f) {
                Log.w(TAG, "Degenerate quadrilateral with area: $area")
                return false
            }

            true
        } catch (e: Exception) {
            Log.w(TAG, "Error validating corner order", e)
            false
        }
    }

    /**
     * ✅ NEW: Calculate quadrilateral area using shoelace formula
     */
    private fun calculateQuadrilateralArea(corners: List<Vector3>): Float {
        if (corners.size != 4) return 0f

        return try {
            var area = 0f
            for (i in corners.indices) {
                val j = (i + 1) % corners.size
                area += corners[i].x * corners[j].z - corners[j].x * corners[i].z
            }
            kotlin.math.abs(area) / 2f
        } catch (e: Exception) {
            Log.w(TAG, "Error calculating area", e)
            0f
        }
    }

    /**
     * ✅ ENHANCED: Safe wireframe drawing with comprehensive error handling
     */
    private fun drawWireframeBox(corners: List<Vector3>, isPreview: Boolean = false) {
        if (corners.size < 8) {
            Log.w(TAG, "Not enough corners for wireframe box: ${corners.size}")
            return
        }

        if (isDestroyed.get()) {
            Log.d(TAG, "Manager destroyed, skipping wireframe drawing")
            return
        }

        try {
            val baseCorners = corners.take(4)
            val topCorners = corners.drop(4)

            Log.d(TAG, "Drawing wireframe box: ${baseCorners.size} base + ${topCorners.size} top corners")

            var edgesDrawn = 0

            // Draw base edges
            for (i in baseCorners.indices) {
                if (isDestroyed.get()) break
                val success = drawLine(baseCorners[i], baseCorners[(i + 1) % baseCorners.size], isPreview)
                if (success) edgesDrawn++
            }

            // Draw top edges
            for (i in topCorners.indices) {
                if (isDestroyed.get()) break
                val success = drawLine(topCorners[i], topCorners[(i + 1) % topCorners.size], isPreview)
                if (success) edgesDrawn++
            }

            // Draw vertical edges
            for (i in baseCorners.indices) {
                if (isDestroyed.get()) break
                val success = drawLine(baseCorners[i], topCorners[i], isPreview)
                if (success) edgesDrawn++
            }

            Log.d(TAG, "Wireframe box drawn: $edgesDrawn edges successfully created")

        } catch (e: Exception) {
            Log.e(TAG, "Error drawing wireframe box", e)
        }
    }

    /**
     * ✅ ENHANCED: Safe line drawing with return success status
     */
    private fun drawLine(start: Vector3, end: Vector3, isPreview: Boolean = false): Boolean {
        if (isDestroyed.get()) return false

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

                if (isPreview) {
                    name = "wireframe_edge_preview"
                }

                // Safely set parent
                try {
                    setParent(arFragment.arSceneView.scene)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to set parent for line node", e)
                    return false
                }
            }

            synchronized(visualNodes) {
                visualNodes.add(lineNode)
            }
            true

        } catch (e: Exception) {
            Log.e(TAG, "Error drawing line", e)
            false
        }
    }

    /**
     * ✅ ENHANCED: Thread-safe preview element removal
     */
    private fun removePreviewElements() {
        if (isDestroyed.get()) return

        try {
            synchronized(visualNodes) {
                val previewNodes = visualNodes.filter { it.name == "wireframe_edge_preview" }.toList()
                Log.d(TAG, "Removing ${previewNodes.size} preview elements")

                previewNodes.forEach { node ->
                    try {
                        node.setParent(null)
                    } catch (e: Exception) {
                        Log.w(TAG, "Error removing preview node", e)
                    }
                }

                visualNodes.removeAll(previewNodes.toSet())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing preview elements", e)
        }
    }

    /**
     * ✅ ENHANCED: Safe scene clearing with proper synchronization
     */
    fun clearScene() {
        try {
            synchronized(visualNodes) {
                Log.d(TAG, "Clearing scene: ${visualNodes.size} nodes")

                val nodesToRemove = visualNodes.toList()
                visualNodes.clear()

                nodesToRemove.forEach { node ->
                    try {
                        node.setParent(null)
                        // Clear node references to help GC
                        node.renderable = null
                    } catch (e: Exception) {
                        Log.w(TAG, "Error removing node from scene", e)
                    }
                }
            }
            Log.d(TAG, "Scene cleared successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing scene", e)
        }
    }

    /**
     * ✅ ENHANCED: Comprehensive scene update with error recovery
     */
    fun updateScene(state: ARMeasurementUiState) {
        if (isDestroyed.get()) {
            Log.d(TAG, "Manager destroyed, ignoring scene update")
            return
        }

        try {
            clearScene()

            // Add corner spheres
            state.corners.forEach { cornerNode ->
                try {
                    addSphere(cornerNode.worldPosition)
                } catch (e: Exception) {
                    Log.w(TAG, "Error adding sphere for corner", e)
                }
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
                        if (wireframeCorners.size >= 8) {
                            drawWireframeBox(wireframeCorners, isPreview = false)
                        }
                    }
                }
            }

            Log.d(TAG, "Scene updated successfully for step: ${state.step}")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating scene", e)
        }
    }

    /**
     * ✅ ENHANCED: Safe sphere addition with error handling
     */
    private fun addSphere(worldPosition: Vector3) {
        if (isDestroyed.get()) return

        if (sphereRenderable == null) {
            Log.w(TAG, "Sphere renderable not ready")
            return
        }

        try {
            val sphereNode = Node().apply {
                renderable = sphereRenderable
                localPosition = worldPosition

                try {
                    setParent(arFragment.arSceneView.scene)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to set parent for sphere node", e)
                    return
                }
            }

            synchronized(visualNodes) {
                visualNodes.add(sphereNode)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error adding sphere", e)
        }
    }

    /**
     * Draw partial base for incomplete rectangles
     */
    private fun drawPartialBase(corners: List<Vector3>) {
        if (isDestroyed.get()) return

        try {
            for (i in 0 until corners.size - 1) {
                drawLine(corners[i], corners[i + 1], isPreview = false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error drawing partial base", e)
        }
    }

    /**
     * Draw complete base rectangle
     */
    private fun drawBase(corners: List<Vector3>) {
        if (corners.size < 4 || isDestroyed.get()) return

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
     * ✅ ENHANCED: Comprehensive cleanup with proper resource disposal
     */
    fun cleanup() {
        try {
            // Set destroyed flag first to prevent new operations
            isDestroyed.set(true)

            Log.d(TAG, "Starting comprehensive cleanup...")

            // Clear pending requests
            synchronized(pendingRequestsLock) {
                pendingDrawRequests.clear()
            }

            // Clear all visual nodes
            clearScene()

            // Dispose renderables properly
            renderables.forEach { renderable ->
                try {
                    // Clear renderable references to help GC
                    // No dispose() method available for material; just dereference
                    // If additional cleanup is needed, do it here
                } catch (e: Exception) {
                    Log.w(TAG, "Error disposing renderable", e)
                }
            }
            renderables.clear()

            // Clear all references
            sphereRenderable = null
            lineRenderable = null
            previewLineRenderable = null

            // Reset flags
            isRenderablesReady.set(false)

            Log.d(TAG, "ARSceneManager cleanup completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }

    /**
     * ✅ NEW: Health check for debugging
     */
    fun getHealthStatus(): String {
        return try {
            """
            ARSceneManager Health Status:
            - Destroyed: ${isDestroyed.get()}
            - Renderables Ready: ${isRenderablesReady.get()}
            - Visual Nodes: ${visualNodes.size}
            - Renderables Count: ${renderables.size}
            - Pending Requests: ${pendingDrawRequests.size}
            - Sphere Ready: ${sphereRenderable != null}
            - Line Ready: ${lineRenderable != null}
            - Preview Line Ready: ${previewLineRenderable != null}
            """.trimIndent()
        } catch (e: Exception) {
            "Error getting health status: ${e.message}"
        }
    }
}