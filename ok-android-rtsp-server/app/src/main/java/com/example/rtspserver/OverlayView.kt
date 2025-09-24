package com.example.rtspserver

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.max

class OverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private var results: List<ObjectDetector.DetectionResult> = emptyList()
    private val paint = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 8.0f
        textSize = 60.0f
    }
    private var scaleFactor = 1.0f

    fun setResults(detectionResults: List<ObjectDetector.DetectionResult>, imageWidth: Int, imageHeight: Int) {
        results = detectionResults

        // Calculate the scale factor to adjust bounding boxes to the view's size
        scaleFactor = max(width.toFloat() / imageWidth, height.toFloat() / imageHeight)

        // Invalidate the view to trigger a redraw
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        results.forEach { result ->
            val boundingBox = result.boundingBox

            // Adjust the bounding box coordinates to the view's scale
            val scaledBox = RectF(
                boundingBox.left * scaleFactor,
                boundingBox.top * scaleFactor,
                boundingBox.right * scaleFactor,
                boundingBox.bottom * scaleFactor
            )

            canvas.drawRect(scaledBox, paint)
            canvas.drawText(result.text, scaledBox.left, scaledBox.top - 10, paint)
        }
    }
}