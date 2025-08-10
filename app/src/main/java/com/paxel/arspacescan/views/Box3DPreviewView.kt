package com.paxel.arspacescan.ui.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

class Box3DPreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val fillPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        alpha = 30
    }

    private var boxLength = 1f
    private var boxWidth = 1f
    private var boxHeight = 1f

    fun setDimensions(length: Float, width: Float, height: Float) {
        // Handle potential zero dimension to avoid division by zero
        val maxDim = maxOf(length, width, height).let { if (it == 0f) 1f else it }
        boxLength = length / maxDim
        boxWidth = width / maxDim
        boxHeight = height / maxDim
        invalidate() // Redraw the view with new dimensions
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centerX = width / 2f
        val centerY = height / 2f
        val scale = minOf(width, height) * 0.3f

        // Calculate 3D projection points (isometric view)
        val angle = Math.toRadians(30.0)

        // Define box vertices in 3D space relative to its center
        val vertices = arrayOf(
            // Bottom face
            floatArrayOf(-boxLength / 2, -boxHeight / 2, -boxWidth / 2),
            floatArrayOf(boxLength / 2, -boxHeight / 2, -boxWidth / 2),
            floatArrayOf(boxLength / 2, -boxHeight / 2, boxWidth / 2),
            floatArrayOf(-boxLength / 2, -boxHeight / 2, boxWidth / 2),
            // Top face
            floatArrayOf(-boxLength / 2, boxHeight / 2, -boxWidth / 2),
            floatArrayOf(boxLength / 2, boxHeight / 2, -boxWidth / 2),
            floatArrayOf(boxLength / 2, boxHeight / 2, boxWidth / 2),
            floatArrayOf(-boxLength / 2, boxHeight / 2, boxWidth / 2)
        )

        // Project 3D vertices to 2D screen coordinates
        val projectedPoints = vertices.map { v ->
            val x = centerX + scale * (v[0] * Math.cos(angle) - v[2] * Math.sin(angle)).toFloat()
            val y = centerY - scale * (v[1] + (v[0] * Math.sin(angle) + v[2] * Math.cos(angle)) * 0.5f).toFloat()
            floatArrayOf(x, y)
        }

        // Draw faces (from back to front for correct layering)
        drawFace(canvas, projectedPoints, intArrayOf(2, 3, 7, 6), Color.parseColor("#FF6666")) // Back
        drawFace(canvas, projectedPoints, intArrayOf(3, 0, 4, 7), Color.parseColor("#FF4D4D")) // Left
        drawFace(canvas, projectedPoints, intArrayOf(0, 1, 2, 3), Color.parseColor("#FFE5E5")) // Bottom
        drawFace(canvas, projectedPoints, intArrayOf(1, 2, 6, 5), Color.parseColor("#FF8080")) // Right
        drawFace(canvas, projectedPoints, intArrayOf(0, 1, 5, 4), Color.parseColor("#FF9999")) // Front
        drawFace(canvas, projectedPoints, intArrayOf(4, 5, 6, 7), Color.parseColor("#FFB3B3")) // Top

        // Draw edges
        paint.color = Color.parseColor("#FF0000")
        val edges = arrayOf(
            intArrayOf(0, 1), intArrayOf(1, 2), intArrayOf(2, 3), intArrayOf(3, 0), // Bottom
            intArrayOf(4, 5), intArrayOf(5, 6), intArrayOf(6, 7), intArrayOf(7, 4), // Top
            intArrayOf(0, 4), intArrayOf(1, 5), intArrayOf(2, 6), intArrayOf(3, 7)  // Connecting
        )

        edges.forEach { edge ->
            canvas.drawLine(
                projectedPoints[edge[0]][0], projectedPoints[edge[0]][1],
                projectedPoints[edge[1]][0], projectedPoints[edge[1]][1],
                paint
            )
        }
    }

    private fun drawFace(canvas: Canvas, points: List<FloatArray>, indices: IntArray, color: Int) {
        val path = Path()
        path.moveTo(points[indices[0]][0], points[indices[0]][1])
        indices.drop(1).forEach { i ->
            path.lineTo(points[i][0], points[i][1])
        }
        path.close()

        fillPaint.color = color
        canvas.drawPath(path, fillPaint)
    }
}