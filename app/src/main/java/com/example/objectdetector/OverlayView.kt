package com.example.objectdetector

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

class OverlayView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {

    private var results: List<ObjectDetectorHelper.DetectionResult> = listOf()
    private val boxPaint = Paint()
    private val textBackgroundPaint = Paint()
    private val textPaint = Paint()

    private var scaleFactorX: Float = 1f
    private var scaleFactorY: Float = 1f
    private var offsetX: Float = 0f
    private var offsetY: Float = 0f

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

            val left = boundingBox.left * scaleFactorX + offsetX
            val top = boundingBox.top * scaleFactorY + offsetY
            val right = boundingBox.right * scaleFactorX + offsetX
            val bottom = boundingBox.bottom * scaleFactorY + offsetY

            canvas.drawRect(left, top, right, bottom, boxPaint)

            val text = "${result.label} ${String.format("%.2f", result.score)}"
            val textBounds = Rect()
            textPaint.getTextBounds(text, 0, text.length, textBounds)
            val textHeight = textBounds.height()
            val textWidth = textPaint.measureText(text)

            canvas.drawRect(
                left,
                top - textHeight,
                left + textWidth,
                top,
                textBackgroundPaint
            )
            canvas.drawText(text, left, top, textPaint)
        }
    }

    fun setResults(
        detectionResults: List<ObjectDetectorHelper.DetectionResult>,
        imageHeight: Int,
        imageWidth: Int,
    ) {
        results = detectionResults

        // This is crucial. Since the PreviewView is using 'fitCenter', we must calculate the scale
        // and offset to correctly map the model's 640x640 coordinates to the view's coordinates.
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()

        val imageAspectRatio = imageWidth.toFloat() / imageHeight.toFloat()
        val viewAspectRatio = viewWidth / viewHeight

        if (imageAspectRatio > viewAspectRatio) {
            // Image is wider than the view. Scale based on width.
            scaleFactorX = viewWidth / ObjectDetectorHelper.INPUT_SIZE
            scaleFactorY = viewWidth / imageAspectRatio / ObjectDetectorHelper.INPUT_SIZE
            offsetX = 0f
            offsetY = (viewHeight - scaleFactorY * ObjectDetectorHelper.INPUT_SIZE) / 2
        } else {
            // Image is taller than or same aspect ratio as the view. Scale based on height.
            scaleFactorX = viewHeight * imageAspectRatio / ObjectDetectorHelper.INPUT_SIZE
            scaleFactorY = viewHeight / ObjectDetectorHelper.INPUT_SIZE
            offsetX = (viewWidth - scaleFactorX * ObjectDetectorHelper.INPUT_SIZE) / 2
            offsetY = 0f
        }

        invalidate()
    }
}