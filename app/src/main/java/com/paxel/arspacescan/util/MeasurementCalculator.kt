package com.paxel.arspacescan.util

import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.math.Vector3
import com.paxel.arspacescan.data.model.BoxResult
import com.paxel.arspacescan.data.model.MeasurementResult
import kotlin.math.abs

class MeasurementCalculator {

    fun calculateBaseCorners(p1: AnchorNode, p2: AnchorNode): List<AnchorNode> {
        val v1 = p1.worldPosition
        val v2 = p2.worldPosition
        val avgY = (v1.y + v2.y) / 2f

        val posA = Vector3(v1.x, avgY, v1.z)
        val posC = Vector3(v2.x, avgY, v2.z)
        val posB = Vector3(v1.x, avgY, v2.z)
        val posD = Vector3(v2.x, avgY, v1.z)

        return listOf(posA, posB, posC, posD).map { position ->
            val node = AnchorNode()
            node.worldPosition = position
            node
        }
    }

    fun calculate3DBox(baseCorners: List<AnchorNode>, heightPoint: AnchorNode): BoxResult? {
        if (baseCorners.size < 4) return null

        val pA = baseCorners[0].worldPosition
        val pB = baseCorners[1].worldPosition
        val pD = baseCorners[3].worldPosition
        val height = abs(heightPoint.worldPosition.y - pA.y)

        val length = Vector3.subtract(pB, pA).length() * 100
        val width = Vector3.subtract(pD, pA).length() * 100
        val heightCm = height * 100

        if (length == 0f || width == 0f || heightCm <= 0.1f) return null

        val volume = length * width * heightCm

        val allCorners = baseCorners.toMutableList()
        baseCorners.forEach { baseNode ->
            val topNode = AnchorNode()
            val pos = baseNode.worldPosition
            val newPosition = Vector3(pos.x, pos.y + height, pos.z)
            topNode.worldPosition = newPosition
            allCorners.add(topNode)
        }

        val measurement = MeasurementResult(
            length = length,
            width = width,
            height = heightCm,
            volume = volume
        )
        return BoxResult(measurement, allCorners)
    }

    fun calculateVolumetricWeight(volumeCm3: Float): Float {
        return volumeCm3 / 6000f
    }
}
