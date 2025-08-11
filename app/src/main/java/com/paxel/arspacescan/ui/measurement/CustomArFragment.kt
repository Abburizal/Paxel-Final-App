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
        val config = Config(session)

        try {
            // Disable light estimation completely to prevent the crash
            config.lightEstimationMode = Config.LightEstimationMode.DISABLED

            // Enable plane detection for measurement
            config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL

            // Update focus mode for better tracking
            config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE

            // Additional safety configuration
            config.focusMode = Config.FocusMode.AUTO

            Log.d("CustomArFragment", "Session configuration created successfully")

        } catch (e: Exception) {
            Log.e("CustomArFragment", "Error configuring session", e)
            // Fallback to minimal configuration
            config.lightEstimationMode = Config.LightEstimationMode.DISABLED
            config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
        }

        return config
    }

    override fun setupSession(session: Session): Boolean {
        try {
            return super.setupSession(session)
        } catch (e: Exception) {
            Log.e("CustomArFragment", "Setup session failed", e)
            return false
        }
    }
}
