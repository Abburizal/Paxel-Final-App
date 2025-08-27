package com.paxel.arspacescan.ui.measurement

import android.content.Context
import android.util.Log
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.sceneform.ux.ArFragment

/**
 * Consolidated AR Fragment for package measurement
 * with comprehensive error handling and optimal configuration
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

    override fun onAttach(context: Context) {
        super.onAttach(context)
        Log.d(TAG, "Fragment attached to context: ${context::class.simpleName}")
    }

    override fun getSessionConfiguration(session: Session): Config {
        Log.d(TAG, "Configuring AR session...")
        val config = super.getSessionConfiguration(session)

        try {
            // Primary configuration - Optimal for package measurement
            configureOptimalSettings(config)
            Log.d(TAG, "AR session configured successfully with optimal settings")

        } catch (e: Exception) {
            Log.w(TAG, "Failed to apply optimal configuration, applying fallback", e)

            try {
                // Fallback strategy - Reduced functionality but stable
                configureFallbackSettings(config)
                Log.d(TAG, "AR session configured with fallback settings")

            } catch (fallbackError: Exception) {
                Log.e(TAG, "Failed to apply fallback configuration, using minimal settings", fallbackError)

                try {
                    // Minimal configuration - Absolute minimum for basic functionality
                    configureMinimalSettings(config)
                    Log.d(TAG, "AR session configured with minimal settings")

                } catch (minimalError: Exception) {
                    Log.e(TAG, "Even minimal configuration failed - AR may not work properly", minimalError)
                }
            }
        }

        return config
    }

    /**
     * Optimal configuration - Best performance and accuracy
     */
    private fun configureOptimalSettings(config: Config) {
        try {
            // CRITICAL: Disable light estimation to prevent crashes
            config.lightEstimationMode = Config.LightEstimationMode.DISABLED

            // Enable both horizontal and vertical plane detection for comprehensive measurement
            config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL

            // Use latest camera image for better tracking accuracy
            config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE

            // Auto focus for sharp measurement points
            config.focusMode = Config.FocusMode.AUTO

            Log.d(TAG, "Applied optimal AR configuration: " +
                    "lightEstimation=DISABLED, " +
                    "planeFinding=HORIZONTAL_AND_VERTICAL, " +
                    "updateMode=LATEST_CAMERA_IMAGE, " +
                    "focusMode=AUTO")
        } catch (e: Exception) {
            Log.e(TAG, "Error applying optimal settings", e)
            throw e
        }
    }

    /**
     * Fallback configuration - Reduced functionality but more stable
     */
    private fun configureFallbackSettings(config: Config) {
        try {
            // Essential: Light estimation must be disabled
            config.lightEstimationMode = Config.LightEstimationMode.DISABLED

            // Horizontal only for basic measurement (more stable)
            config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL

            // Basic update mode
            config.updateMode = Config.UpdateMode.BLOCKING

            // Fixed focus to avoid focus hunting
            config.focusMode = Config.FocusMode.FIXED

            Log.d(TAG, "Applied fallback AR configuration: " +
                    "lightEstimation=DISABLED, " +
                    "planeFinding=HORIZONTAL, " +
                    "updateMode=BLOCKING, " +
                    "focusMode=FIXED")
        } catch (e: Exception) {
            Log.e(TAG, "Error applying fallback settings", e)
            throw e
        }
    }

    /**
     * Minimal configuration - Last resort for problematic devices
     */
    private fun configureMinimalSettings(config: Config) {
        try {
            // Only the absolute essentials
            config.lightEstimationMode = Config.LightEstimationMode.DISABLED
            config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL

            Log.d(TAG, "Applied minimal AR configuration: " +
                    "lightEstimation=DISABLED, " +
                    "planeFinding=HORIZONTAL")
        } catch (e: Exception) {
            Log.e(TAG, "Error applying minimal settings", e)
            throw e
        }
    }

    override fun onResume() {
        try {
            super.onResume()
            Log.d(TAG, "Fragment resuming...")

            // Ensure AR scene is properly configured
            configureSceneOnResume()

            Log.d(TAG, "Fragment resumed successfully with AR scene configured")

        } catch (e: Exception) {
            Log.e(TAG, "Error during fragment resume", e)

            // Recovery strategy
            try {
                recoverFromResumeFailure()
            } catch (recoveryError: Exception) {
                Log.e(TAG, "Failed to recover from resume failure", recoveryError)
            }
        }
    }

    /**
     * Enhanced scene configuration
     */
    private fun configureSceneOnResume() {
        try {
            arSceneView?.let { sceneView ->
                // Enable plane rendering for visual feedback
                sceneView.planeRenderer.isEnabled = true
                sceneView.planeRenderer.isVisible = true

                // Double-ensure light estimation is disabled (critical for stability)
                sceneView.isLightEstimationEnabled = false

                Log.d(TAG, "AR scene configured: planes enabled, light estimation disabled")
            } ?: run {
                Log.w(TAG, "ArSceneView not available during resume - may initialize later")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error configuring scene on resume", e)
        }
    }

    /**
     * Recovery strategy
     */
    private fun recoverFromResumeFailure() {
        Log.d(TAG, "Attempting recovery from resume failure...")

        try {
            // Try to reinitialize scene view if available
            arSceneView?.let { sceneView ->
                // Basic recovery steps
                sceneView.planeRenderer.isEnabled = true
                sceneView.isLightEstimationEnabled = false

                Log.d(TAG, "Basic scene recovery applied successfully")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Scene recovery failed", e)
        }
    }

    override fun onPause() {
        Log.d(TAG, "Fragment pausing...")

        try {
            // Cleanup before pause
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
     * Resource cleanup
     */
    private fun cleanupSceneResources() {
        try {
            arSceneView?.let { sceneView ->
                // Clear any pending rendering operations
                sceneView.scene.removeOnUpdateListener(null)

                // Clear any registered touch listeners to prevent leaks
                sceneView.scene.setOnTouchListener(null)

                Log.d(TAG, "Scene resources cleaned up successfully")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Minor cleanup issues (non-critical)", e)
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "Fragment being destroyed...")

        try {
            // Comprehensive cleanup
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
     * Final cleanup - Release all resources
     */
    private fun performFinalCleanup() {
        try {
            arSceneView?.let { sceneView ->
                // Stop any ongoing operations
                sceneView.pause()

                // Destroy the scene view
                sceneView.destroy()
                Log.d(TAG, "ArSceneView destroyed successfully")
            }
        } catch (e: Exception) {
            Log.w(TAG, "ArSceneView cleanup issues (non-critical)", e)
        }
    }

    // ===== DIAGNOSTIC AND UTILITY METHODS =====

    /**
     * Check AR session health
     */
    fun isArSessionHealthy(): Boolean {
        return try {
            val session = arSceneView?.session
            val sceneView = arSceneView

            // Basic health check
            session != null && sceneView != null
        } catch (e: Exception) {
            Log.w(TAG, "Error checking AR session health", e)
            false
        }
    }

    /**
     * Get current AR configuration summary
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
                - Session Healthy: ${isArSessionHealthy()}
                - Scene Available: ${arSceneView != null}
                - Planes Enabled: ${arSceneView?.planeRenderer?.isEnabled ?: false}
                """.trimIndent()
            }
        } catch (e: Exception) {
            "Error getting configuration: ${e.message}"
        }
    }

    /**
     * Check if ready for measurement
     */
    fun isReadyForMeasurement(): Boolean {
        return try {
            val sceneView = arSceneView ?: return false
            val session = sceneView.session ?: return false
            val arFrame = sceneView.arFrame ?: return false

            // Use arFrame.camera instead of session.camera
            val isTracking = arFrame.camera.trackingState == TrackingState.TRACKING
            val hasPlanes = session.getAllTrackables(com.google.ar.core.Plane::class.java).isNotEmpty()

            isTracking && hasPlanes
        } catch (e: Exception) {
            Log.w(TAG, "Error checking measurement readiness", e)
            false
        }
    }

    /**
     * Get performance metrics
     */
    fun getPerformanceMetrics(): String {
        return try {
            val sceneView = arSceneView
            if (sceneView == null) {
                "AR Scene not available"
            } else {
                val session = sceneView.session
                val arFrame = sceneView.arFrame

                """
                AR Performance Metrics:
                - Frame Available: ${arFrame != null}
                - Session Available: ${session != null}
                - Scene Nodes: ${sceneView.scene.children.size}
                - Memory Usage: ${Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()} bytes
                - Available Planes: ${session?.getAllTrackables(com.google.ar.core.Plane::class.java)?.size ?: 0}
                - Camera Tracking: ${arFrame?.camera?.trackingState ?: "Unknown"}
                """.trimIndent()
            }
        } catch (e: Exception) {
            "Error getting performance metrics: ${e.message}"
        }
    }

    /**
     * Check if AR is actively tracking
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
}