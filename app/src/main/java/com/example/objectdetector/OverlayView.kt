package com.example.objectdetector

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.max

class OverlayView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {

    private var results: List<ObjectDetectorHelper.DetectionResult> = listOf()
    private var boxPaint = Paint()
    private var textBackgroundPaint = Paint()
    private var textPaint = Paint()

    private var scaleFactor: Float = 1f

    init {
        initPaints()
    }

    private fun initPaints() {
        textBackgroundPaint.color = Color.BLACK
        textBackgroundPaint.style = Paint.Style.FILL
        textBackgroundPaint.textSize = 50f

        textPaint.color = Color.WHITE
        textPaint.style = Paint.Style.FILL
        textPaint.textSize = 50f

        boxPaint.color = Color.RED
        boxPaint.strokeWidth = 8F
        boxPaint.style = Paint.Style.STROKE
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)

        for (result in results) {
            val boundingBox = result.boundingBox

            // The coordinates from the helper are NOT scaled to the input size, but to the original image size.
            // We need to scale them to the view size, respecting aspect ratio.
            val top = boundingBox.top * scaleFactor
            val bottom = boundingBox.bottom * scaleFactor
            val left = boundingBox.left * scaleFactor
            val right = boundingBox.right * scaleFactor

            canvas.drawRect(left, top, right, bottom, boxPaint)

            val drawableText = result.text

            val textBounds = android.graphics.Rect()
            textPaint.getTextBounds(drawableText, 0, drawableText.length, textBounds)
            val textHeight = textBounds.height()
            val textWidth = textPaint.measureText(drawableText)

            canvas.drawRect(
                left,
                top,
                left + textWidth + 8,
                top + textHeight + 8,
                textBackgroundPaint
            )

            canvas.drawText(drawableText, left, top + textHeight, textPaint)
        }
    }

    fun setResults(
        detectionResults: List<ObjectDetectorHelper.DetectionResult>,
        imageHeight: Int,
        imageWidth: Int,
    ) {
        results = detectionResults

        // Calcaulate the scale factor to map coordinates from the image to the view.
        scaleFactor = max(width * 1f / imageWidth, height * 1f / imageHeight)
        invalidate()
    }
}