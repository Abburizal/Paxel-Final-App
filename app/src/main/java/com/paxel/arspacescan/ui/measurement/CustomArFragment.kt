package com.paxel.arspacescan.ui.measurement

import android.content.Context
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.sceneform.ux.ArFragment

class CustomArFragment : ArFragment() {
    
    override fun onAttach(context: Context) {
        super.onAttach(context)
    }
    
    override fun onCreateSessionConfig(session: Session): Config {
        val config = super.onCreateSessionConfig(session)

        // Disable light estimation to prevent the acquireEnvironmentalHdrCubeMap() crash
        config.lightEstimationMode = Config.LightEstimationMode.DISABLED
        
        // Enable plane detection for measurement
        config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
        
        // Update focus mode for better tracking
        config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE

        return config
    }
}
