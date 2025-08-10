package com.paxel.arspacescan.data.model

import com.google.ar.sceneform.AnchorNode

data class BoxResult(
    val measurement: MeasurementResult,
    val allCorners: List<AnchorNode>
)
