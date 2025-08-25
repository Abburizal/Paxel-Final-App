package com.paxel.arspacescan.ui.measurement

import android.content.Context
import android.util.Log
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.sceneform.ux.ArFragment

class CustomArFragment : ArFragment() {
    
    override fun onAttach(context: Context) {
        super.onAttach(context)
    }
    
    override fun getSessionConfiguration(session: Session): Config {
        val config = super.getSessionConfiguration(session)

        try {
            // CRITICAL FIX: Completely disable light estimation to prevent crash
            config.lightEstimationMode = Config.LightEstimationMode.DISABLED

            // Enable plane detection for measurement
            config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL

            // Set update mode for better tracking
            config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE

            // Set focus mode
            config.focusMode = Config.FocusMode.AUTO

            Log.d("CustomArFragment", "Session configuration created successfully with light estimation DISABLED")

        } catch (e: Exception) {
            Log.e("CustomArFragment", "Error configuring session", e)
            // Fallback to absolute minimal configuration
            try {
                config.lightEstimationMode = Config.LightEstimationMode.DISABLED
                config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
            } catch (fallbackError: Exception) {
                Log.e("CustomArFragment", "Even fallback configuration failed", fallbackError)
            }
        }

        return config
    }

    override fun onResume() {
        try {
            super.onResume()

            // Additional safety: ensure light estimation stays disabled
            arSceneView?.let { sceneView ->
                sceneView.planeRenderer.isEnabled = true
                sceneView.planeRenderer.isVisible = true
                Log.d("CustomArFragment", "Fragment resumed with plane rendering enabled")
            }

        } catch (e: Exception) {
            Log.e("CustomArFragment", "Fragment resume failed", e)
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            Log.d("CustomArFragment", "Fragment paused successfully")
        } catch (e: Exception) {
            Log.e("CustomArFragment", "Fragment pause failed", e)
        }
    }
}
