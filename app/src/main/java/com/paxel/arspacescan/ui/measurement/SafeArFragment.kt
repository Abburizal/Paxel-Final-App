package com.paxel.arspacescan.ui.measurement

import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.sceneform.ux.ArFragment

/**
 * Custom ArFragment that disables problematic light estimation features
 * to prevent crashes with incompatible ARCore/Sceneform versions
 */
class SafeArFragment : ArFragment() {

    override fun getSessionConfiguration(session: Session): Config {
        val config = super.getSessionConfiguration(session)

        // Disable light estimation to prevent the acquireEnvironmentalHdrCubeMap crash
        config.lightEstimationMode = Config.LightEstimationMode.DISABLED

        // Set plane finding mode to horizontal only for better performance
        config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL

        return config
    }
}
