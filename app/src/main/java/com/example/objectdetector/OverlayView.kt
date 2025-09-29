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

    private var results: List<ObjectTracker.TrackedObject> = listOf()
    private val boxPaint = Paint()
    private val textBackgroundPaint = Paint()
    private val textPaint = Paint()

    private var scaleFactorX: Float = 1f
    private var scaleFactorY: Float = 1f
    private var offsetX: Float = 0f
    private var offsetY: Float = 0f

    // A list of colors to assign to different tracked objects.
    private val colorPalette = listOf(
        Color.RED, Color.GREEN, Color.BLUE, Color.CYAN, Color.MAGENTA, Color.YELLOW,
        Color.parseColor("#FF5733"), Color.parseColor("#33FF57"), Color.parseColor("#3357FF"),
        Color.parseColor("#FF33A1"), Color.parseColor("#A133FF"), Color.parseColor("#33FFA1")
    )

    init {
        initPaints()
    }

    private fun initPaints() {
        textBackgroundPaint.style = Paint.Style.FILL
        textBackgroundPaint.textSize = 50f

        textPaint.color = Color.WHITE
        textPaint.style = Paint.Style.FILL
        textPaint.textSize = 50f

        boxPaint.strokeWidth = 8F
        boxPaint.style = Paint.Style.STROKE
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)

        for (result in results) {
            val color = colorPalette[result.id % colorPalette.size]
            boxPaint.color = color
            textBackgroundPaint.color = color

            val boundingBox = result.boundingBox

            val left = boundingBox.left * scaleFactorX + offsetX
            val top = boundingBox.top * scaleFactorY + offsetY
            val right = boundingBox.right * scaleFactorX + offsetX
            val bottom = boundingBox.bottom * scaleFactorY + offsetY

            canvas.drawRect(left, top, right, bottom, boxPaint)

            val text = "#${result.id}: ${result.label} ${String.format("%.2f", result.score)}"
            val textBounds = Rect()
            textPaint.getTextBounds(text, 0, text.length, textBounds)
            val textHeight = textBounds.height()
            val textWidth = textPaint.measureText(text)

            canvas.drawRect(
                left,
                top - textHeight - 8, // Move text background slightly above the box
                left + textWidth + 8,
                top,
                textBackgroundPaint
            )
            canvas.drawText(text, left + 4, top - 4, textPaint) // Add a little padding
        }
    }

    fun setResults(
        detectionResults: List<ObjectTracker.TrackedObject>,
        imageHeight: Int,
        imageWidth: Int,
    ) {
        results = detectionResults

        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()

        val imageAspectRatio = imageWidth.toFloat() / imageHeight.toFloat()
        val viewAspectRatio = viewWidth / viewHeight

        if (imageAspectRatio > viewAspectRatio) {
            scaleFactorX = viewWidth / ObjectDetectorHelper.INPUT_SIZE
            scaleFactorY = viewWidth / imageAspectRatio / ObjectDetectorHelper.INPUT_SIZE
            offsetX = 0f
            offsetY = (viewHeight - scaleFactorY * ObjectDetectorHelper.INPUT_SIZE) / 2
        } else {
            scaleFactorX = viewHeight * imageAspectRatio / ObjectDetectorHelper.INPUT_SIZE
            scaleFactorY = viewHeight / ObjectDetectorHelper.INPUT_SIZE
            offsetX = (viewWidth - scaleFactorX * ObjectDetectorHelper.INPUT_SIZE) / 2
            offsetY = 0f
        }

        invalidate()
    }
}