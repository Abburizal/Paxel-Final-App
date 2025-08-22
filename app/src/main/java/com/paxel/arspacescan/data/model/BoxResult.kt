package com.paxel.arspacescan.data.model

import com.google.ar.sceneform.AnchorNode

data class BoxResult(
    val length: Float, // dalam meter
    val width: Float,  // dalam meter
    val height: Float, // dalam meter
    val volume: Float, // dalam meter kubik
    val packageName: String
)
