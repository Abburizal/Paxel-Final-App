package com.paxel.arspacescan.data.model

import com.google.ar.sceneform.AnchorNode

data class BoxResult(
    val length: Float,
    val width: Float,
    val height: Float,
    val volume: Float,
    val corners: List<AnchorNode>
)
