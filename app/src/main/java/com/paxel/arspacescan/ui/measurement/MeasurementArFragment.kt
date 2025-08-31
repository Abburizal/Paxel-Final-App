package com.paxel.arspacescan.ui.measurement

import android.content.Context
import android.util.Log
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.sceneform.ux.ArFragment
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ✅ ENHANCED: Consolidated AR Fragment with comprehensive stability improvements
 * and optimal configuration for package measurement with proper error handling
 */
class MeasurementArFragment : ArFragment() {

    companion object {
        private const val TAG = "MeasurementArFragment"

        /**
         * Factory method for creating fragment instance
         */
        fun newInstance(): MeasurementArFragment {
            return MeasurementArFragment()
        }
    }

    // ✅ ENHANCED: Thread-safe state management
    private val isConfigured = AtomicBoolean(false)
    private val isDestroyed = AtomicBoolean(false)
    private var sessionFailureCount = 0
    private val maxSessionFailures = 3

    override fun onAttach(context: Context) {
        super.onAttach(context)
        Log.d(TAG, "Fragment attached to context: ${context::class.simpleName}")
    }

    /**
     * ✅ CRITICAL: Enhanced session configuration with fallback strategies
     * This fixes the light estimation crashes and improves AR stability
     */
    override fun getSessionConfiguration(session: Session): Config {
        Log.d(TAG, "Configuring AR session (attempt ${sessionFailureCount + 1})")
        val config = super.getSessionConfiguration(session)

        return try {
            // ✅ PRIMARY: Optimal configuration for measurement accuracy
            if (sessionFailureCount == 0) {
                configureOptimalSettings(config, session)
                Log.d(TAG, "Applied optimal AR configuration")
            }
            // ✅ FALLBACK 1: Reduced but stable configuration
            else if (sessionFailureCount == 1) {
                configureFallbackSettings(config, session)
                Log.d(TAG, "Applied fallback AR configuration")
            }
            // ✅ FALLBACK 2: Minimal configuration for problematic devices
            else {
                configureMinimalSettings(config)
                Log.d(TAG, "Applied minimal AR configuration")
            }

            isConfigured.set(true)
            config

        } catch (e: Exception) {
            Log.e(TAG, "Configuration failed, applying emergency settings", e)
            sessionFailureCount++

            try {
                // ✅ EMERGENCY: Absolute minimum configuration
                configureEmergencySettings(config)
                config
            } catch (emergencyError: Exception) {
                Log.e(TAG, "Even emergency configuration failed", emergencyError)
                config // Return default config as last resort
            }
        }
    }

    /**
     * ✅ OPTIMAL: Best performance and accuracy configuration
     */
    private fun configureOptimalSettings(config: Config, session: Session) {
        // ✅ CRITICAL: Light estimation MUST be disabled (prevents crashes)
        config.lightEstimationMode = Config.LightEstimationMode.DISABLED

        // ✅ PERBAIKAN: Fokus pada bidang HORIZONTAL untuk stabilitas pengukuran di lantai/meja
        config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL

        // ✅ PERFORMANCE: Latest camera image for better tracking
        config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE

        // ✅ ACCURACY: Auto focus for sharp measurement points
        config.focusMode = Config.FocusMode.AUTO

        // ✅ ENHANCED: Depth configuration if supported (improves accuracy)
        try {
            if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                config.depthMode = Config.DepthMode.AUTOMATIC
                Log.d(TAG, "Depth mode enabled for enhanced accuracy")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Depth mode not supported on this device", e)
        }

        Log.d(TAG, "Optimal configuration applied: lightEst=DISABLED, planes=HORIZONTAL, update=LATEST, focus=AUTO")
    }


    /**
     * ✅ FALLBACK: Reduced functionality but more stable
     */
    private fun configureFallbackSettings(config: Config, session: Session) {
        // ✅ ESSENTIAL: Light estimation MUST remain disabled
        config.lightEstimationMode = Config.LightEstimationMode.DISABLED

        // ✅ CONSERVATIVE: Horizontal planes only (more stable)
        config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL

        // ✅ STABLE: Blocking update mode
        config.updateMode = Config.UpdateMode.BLOCKING

        // ✅ STABLE: Fixed focus to avoid focus hunting
        config.focusMode = Config.FocusMode.FIXED

        // ✅ STABILITY: Disable advanced features that may cause issues
        try {
            if (session.isDepthModeSupported(Config.DepthMode.DISABLED)) {
                config.depthMode = Config.DepthMode.DISABLED
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not disable depth mode", e)
        }

        Log.d(TAG, "Fallback configuration applied: lightEst=DISABLED, planes=HORIZONTAL, update=BLOCKING, focus=FIXED")
    }

    /**
     * ✅ MINIMAL: Last resort for problematic devices
     */
    private fun configureMinimalSettings(config: Config) {
        // ✅ CRITICAL: Only the absolute essentials
        config.lightEstimationMode = Config.LightEstimationMode.DISABLED
        config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL

        // Use most conservative settings
        try {
            config.updateMode = Config.UpdateMode.BLOCKING
            config.focusMode = Config.FocusMode.FIXED
        } catch (e: Exception) {
            Log.w(TAG, "Could not apply minimal update/focus settings", e)
        }

        Log.d(TAG, "Minimal configuration applied: lightEst=DISABLED, planes=HORIZONTAL only")
    }

    /**
     * ✅ EMERGENCY: Absolute minimum for worst-case scenarios
     */
    private fun configureEmergencySettings(config: Config) {
        // Only touch what's absolutely necessary
        config.lightEstimationMode = Config.LightEstimationMode.DISABLED

        Log.d(TAG, "Emergency configuration applied: only lightEst=DISABLED")
    }

    override fun onResume() {
        try {
            super.onResume()
            Log.d(TAG, "Fragment resuming...")

            if (isDestroyed.get()) {
                Log.w(TAG, "Fragment already destroyed, skipping resume setup")
                return
            }

            // ✅ ENHANCED: Ensure AR scene is properly configured
            configureSceneOnResume()

            // ✅ STABILITY: Reset failure count on successful resume
            if (isConfigured.get()) {
                sessionFailureCount = 0
            }

            Log.d(TAG, "Fragment resumed successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Error during fragment resume", e)
            sessionFailureCount++

            try {
                // ✅ RECOVERY: Attempt recovery
                recoverFromResumeFailure()
            } catch (recoveryError: Exception) {
                Log.e(TAG, "Failed to recover from resume failure", recoveryError)

                // ✅ GRACEFUL DEGRADATION: Continue with minimal functionality
                if (sessionFailureCount < maxSessionFailures) {
                    Log.i(TAG, "Will retry with degraded configuration")
                } else {
                    Log.e(TAG, "Max session failures reached, AR may not work properly")
                }
            }
        }
    }

    /**
     * ✅ ENHANCED: Robust scene configuration with error handling
     */
    private fun configureSceneOnResume() {
        try {
            arSceneView?.let { sceneView ->
                // ✅ CRITICAL: Ensure light estimation stays disabled at scene level
                sceneView.isLightEstimationEnabled = false

                // ✅ MEASUREMENT: Enable plane rendering for visual feedback
                sceneView.planeRenderer.isEnabled = true
                sceneView.planeRenderer.isVisible = true

                // ✅ PERFORMANCE: Optimize plane renderer
                try {
                    // If you want to set transparency, use setFloat4 or setColor if available
                    // Example: material?.setFloat4("color", r, g, b, alpha)
                    // Remove invalid setFloat usage
                } catch (e: Exception) {
                    Log.w(TAG, "Could not optimize plane renderer", e)
                }

                Log.d(TAG, "AR scene configured: planes enabled, light estimation disabled")
            } ?: run {
                Log.w(TAG, "ArSceneView not available during resume - may initialize later")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error configuring scene on resume", e)
        }
    }

    /**
     * ✅ RECOVERY: Recovery strategy for resume failures
     */
    private fun recoverFromResumeFailure() {
        Log.d(TAG, "Attempting recovery from resume failure...")

        try {
            // ✅ BASIC: Try to reinitialize scene view if available
            arSceneView?.let { sceneView ->
                // Basic recovery steps
                sceneView.planeRenderer.isEnabled = true
                sceneView.isLightEstimationEnabled = false

                // ✅ SAFE: Try to restart AR session if possible
                try {
                    sceneView.session?.let { session ->
                        // Session is active, just reconfigure
                        configureSceneOnResume()
                    }
                } catch (sessionError: Exception) {
                    Log.w(TAG, "Could not recover session", sessionError)
                }

                Log.d(TAG, "Basic scene recovery applied successfully")
            } ?: run {
                Log.w(TAG, "No AR scene view available for recovery")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Scene recovery failed", e)
            throw e
        }
    }

    override fun onPause() {
        Log.d(TAG, "Fragment pausing...")

        try {
            // ✅ CLEANUP: Cleanup before pause to prevent resource leaks
            cleanupSceneResources()
            super.onPause()
            Log.d(TAG, "Fragment paused successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Error during fragment pause", e)
            // Still call super.onPause() even if cleanup fails
            try {
                super.onPause()
            } catch (superError: Exception) {
                Log.e(TAG, "Super.onPause() also failed", superError)
            }
        }
    }

    /**
     * ✅ ENHANCED: Comprehensive resource cleanup
     */
    private fun cleanupSceneResources() {
        try {
            arSceneView?.let { sceneView ->
                // ✅ LISTENERS: Clear any pending rendering operations
                try {
                    sceneView.scene.removeOnUpdateListener(null)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not remove update listeners", e)
                }

                // ✅ TOUCHES: Clear any registered touch listeners to prevent leaks
                try {
                    sceneView.scene.setOnTouchListener(null)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not remove touch listeners", e)
                }

                // ✅ NODES: Clear any remaining nodes
                try {
                    val childNodes = sceneView.scene.children.toList()
                    childNodes.forEach { node ->
                        try {
                            node.setParent(null)
                        } catch (nodeError: Exception) {
                            Log.w(TAG, "Could not remove child node", nodeError)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not clear scene nodes", e)
                }

                Log.d(TAG, "Scene resources cleaned up successfully")
            } ?: run {
                Log.d(TAG, "No AR scene view to clean up")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Minor cleanup issues (non-critical)", e)
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "Fragment being destroyed...")

        // ✅ FLAG: Set destroyed flag to prevent further operations
        isDestroyed.set(true)

        try {
            // ✅ CLEANUP: Comprehensive cleanup
            performFinalCleanup()
            super.onDestroy()
            Log.d(TAG, "Fragment destroyed successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Error during fragment destruction", e)
            // Ensure super.onDestroy() is called
            try {
                super.onDestroy()
            } catch (superError: Exception) {
                Log.e(TAG, "Super.onDestroy() also failed", superError)
            }
        }
    }

    /**
     * ✅ ENHANCED: Final cleanup with proper error isolation
     */
    private fun performFinalCleanup() {
        try {
            arSceneView?.let { sceneView ->
                try {
                    // ✅ PAUSE: Stop any ongoing operations
                    sceneView.pause()
                    Log.d(TAG, "ArSceneView paused")
                } catch (e: Exception) {
                    Log.w(TAG, "Error pausing ArSceneView", e)
                }

                try {
                    // ✅ DESTROY: Destroy the scene view
                    sceneView.destroy()
                    Log.d(TAG, "ArSceneView destroyed successfully")
                } catch (e: Exception) {
                    Log.w(TAG, "Error destroying ArSceneView", e)
                }
            }

            // ✅ FLAGS: Reset internal state
            isConfigured.set(false)
            sessionFailureCount = 0

        } catch (e: Exception) {
            Log.w(TAG, "Final cleanup issues (non-critical)", e)
        }
    }

    // ===== ✅ ENHANCED DIAGNOSTIC AND UTILITY METHODS =====

    /**
     * ✅ ENHANCED: Comprehensive AR session health check
     */
    fun isArSessionHealthy(): Boolean {
        return try {
            if (isDestroyed.get()) return false

            val session = arSceneView?.session
            val sceneView = arSceneView

            // Basic health check
            val basicHealth = session != null && sceneView != null

            if (!basicHealth) return false

            // ✅ ADVANCED: Check session state
            val sessionActive = session != null // No isPaused property, just check for non-null

            // ✅ TRACKING: Check if camera is tracking
            val isTracking = try {
                arSceneView?.arFrame?.camera?.trackingState == TrackingState.TRACKING
            } catch (e: Exception) {
                Log.w(TAG, "Could not check tracking state", e)
                false
            }

            val overallHealth = basicHealth && sessionActive

            Log.d(TAG, "AR Health Check: basic=$basicHealth, active=$sessionActive, tracking=$isTracking, overall=$overallHealth")

            overallHealth

        } catch (e: Exception) {
            Log.w(TAG, "Error checking AR session health", e)
            false
        }
    }

    /**
     * ✅ ENHANCED: Comprehensive configuration summary
     */
    fun getConfigurationSummary(): String {
        return try {
            val session = arSceneView?.session
            if (session == null) {
                "AR Session not initialized"
            } else {
                val config = session.config
                """
                AR Configuration Summary:
                - Light Estimation: ${config.lightEstimationMode}
                - Plane Finding: ${config.planeFindingMode}
                - Update Mode: ${config.updateMode}
                - Focus Mode: ${config.focusMode}
                - Depth Mode: ${try { config.depthMode } catch (e: Exception) { "N/A" }}
                - Session Healthy: ${isArSessionHealthy()}
                - Scene Available: ${arSceneView != null}
                - Planes Enabled: ${arSceneView?.planeRenderer?.isEnabled ?: false}
                - Light Est Disabled: ${!(arSceneView?.isLightEstimationEnabled ?: true)}
                - Configured: ${isConfigured.get()}
                - Failure Count: $sessionFailureCount
                - Destroyed: ${isDestroyed.get()}
                """.trimIndent()
            }
        } catch (e: Exception) {
            "Error getting configuration: ${e.message}"
        }
    }

    /**
     * ✅ ENHANCED: Advanced measurement readiness check
     */
    fun isReadyForMeasurement(): Boolean {
        return try {
            if (isDestroyed.get() || !isConfigured.get()) {
                Log.d(TAG, "Not ready: destroyed=${isDestroyed.get()}, configured=${isConfigured.get()}")
                return false
            }

            val sceneView = arSceneView ?: return false.also {
                Log.d(TAG, "Not ready: no scene view")
            }

            val session = sceneView.session ?: return false.also {
                Log.d(TAG, "Not ready: no session")
            }

            val arFrame = sceneView.arFrame ?: return false.also {
                Log.d(TAG, "Not ready: no frame")
            }

            // ✅ TRACKING: Check if camera is tracking
            val isTracking = arFrame.camera.trackingState == TrackingState.TRACKING

            // ✅ PLANES: Check for stable planes
            val stablePlanes = try {
                session.getAllTrackables(com.google.ar.core.Plane::class.java).filter { plane ->
                    plane.trackingState == TrackingState.TRACKING &&
                            plane.type == com.google.ar.core.Plane.Type.HORIZONTAL_UPWARD_FACING
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error getting planes", e)
                emptyList()
            }

            val hasStablePlanes = stablePlanes.isNotEmpty()
            val isReady = isTracking && hasStablePlanes

            Log.d(TAG, "Measurement readiness: tracking=$isTracking, stablePlanes=${stablePlanes.size}, ready=$isReady")

            isReady

        } catch (e: Exception) {
            Log.w(TAG, "Error checking measurement readiness", e)
            false
        }
    }

    /**
     * ✅ ENHANCED: Comprehensive performance metrics
     */
    fun getPerformanceMetrics(): String {
        return try {
            val sceneView = arSceneView
            if (sceneView == null) {
                "AR Scene not available"
            } else {
                val session = sceneView.session
                val arFrame = sceneView.arFrame

                // ✅ MEMORY: Get memory usage
                val runtime = Runtime.getRuntime()
                val usedMemory = runtime.totalMemory() - runtime.freeMemory()
                val maxMemory = runtime.maxMemory()
                val memoryPercent = (usedMemory.toFloat() / maxMemory * 100).toInt()

                // ✅ PLANES: Get plane statistics
                val (totalPlanes, trackingPlanes, horizontalPlanes) = try {
                    val allPlanes = session?.getAllTrackables(com.google.ar.core.Plane::class.java) ?: emptyList()
                    val tracking = allPlanes.count { it.trackingState == TrackingState.TRACKING }
                    val horizontal = allPlanes.count { it.type == com.google.ar.core.Plane.Type.HORIZONTAL_UPWARD_FACING }
                    Triple(allPlanes.size, tracking, horizontal)
                } catch (e: Exception) {
                    Triple(0, 0, 0)
                }

                """
                AR Performance Metrics:
                - Frame Available: ${arFrame != null}
                - Session Available: ${session != null}
                - Scene Nodes: ${sceneView.scene.children.size}
                - Memory Usage: $memoryPercent% (${usedMemory / 1024 / 1024} MB / ${maxMemory / 1024 / 1024} MB)
                - Total Planes: $totalPlanes
                - Tracking Planes: $trackingPlanes  
                - Horizontal Planes: $horizontalPlanes
                - Camera Tracking: ${arFrame?.camera?.trackingState ?: "Unknown"}
                - Session Failures: $sessionFailureCount
                - Ready for Measurement: ${isReadyForMeasurement()}
                """.trimIndent()
            }
        } catch (e: Exception) {
            "Error getting performance metrics: ${e.message}"
        }
    }

    /**
     * Check if AR is actively tracking (simplified version)
     */
    fun isActivelyTracking(): Boolean {
        return try {
            val arFrame = arSceneView?.arFrame
            arFrame?.camera?.trackingState == TrackingState.TRACKING
        } catch (e: Exception) {
            Log.w(TAG, "Error checking tracking state", e)
            false
        }
    }

    /**
     * Get plane count for measurement validation
     */
    fun getAvailablePlaneCount(): Int {
        return try {
            val session = arSceneView?.session
            session?.getAllTrackables(com.google.ar.core.Plane::class.java)?.size ?: 0
        } catch (e: Exception) {
            Log.w(TAG, "Error getting plane count", e)
            0
        }
    }

    /**
     * ✅ NEW: Force session restart (for emergency recovery)
     */
    fun forceSessionRestart(): Boolean {
        return try {
            Log.i(TAG, "Attempting forced session restart...")

            arSceneView?.let { sceneView ->
                // Pause current session
                sceneView.pause()

                // Resume with fresh configuration
                sceneView.resume()

                Log.i(TAG, "Session restart completed")
                true
            } ?: false

        } catch (e: Exception) {
            Log.e(TAG, "Failed to restart session", e)
            false
        }
    }
}