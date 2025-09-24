package com.example.rtspserver

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.util.Locale

class ObjectDetector(context: Context, modelName: String, labelsName: String) {

    private var interpreter: Interpreter? = null
    private var labels: List<String> = emptyList()

    private var imageWidth: Int = 0
    private var imageHeight: Int = 0

    init {
        try {
            val model = FileUtil.loadMappedFile(context, modelName)
            labels = FileUtil.loadLabels(context, labelsName)

            val interpreterOptions = Interpreter.Options()
            interpreterOptions.setNumThreads(4)
            interpreter = Interpreter(model, interpreterOptions)

            val inputShape = interpreter!!.getInputTensor(0).shape()
            imageWidth = inputShape[1]
            imageHeight = inputShape[2]

        } catch (e: Exception) {
            Log.e("ObjectDetector", "Error initializing TensorFlow Lite interpreter.", e)
        }
    }

    fun detect(bitmap: Bitmap): List<DetectionResult> {
        if (interpreter == null || imageWidth == 0 || imageHeight == 0) return emptyList()

        val tensorImage = TensorImage(DataType.FLOAT32)
        tensorImage.load(bitmap)

        val imageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(imageHeight, imageWidth, ResizeOp.ResizeMethod.BILINEAR))
            .add(NormalizeOp(0.0f, 255.0f))
            .build()

        val processedImage = imageProcessor.process(tensorImage)

        val outputShape = interpreter!!.getOutputTensor(0).shape()
        val output = Array(outputShape[0]) { Array(outputShape[1]) { FloatArray(outputShape[2]) } }

        interpreter?.run(processedImage.buffer, output)

        return postProcess(output[0], bitmap.width, bitmap.height)
    }

    private fun postProcess(output: Array<FloatArray>, originalWidth: Int, originalHeight: Int): List<DetectionResult> {
        val detections = mutableListOf<DetectionResult>()

        for (detection in output) {
            // THE FINAL FIX: Add a robust safety check for the array size.
            // The model sometimes returns malformed arrays, this will safely ignore them.
            if (detection.size < 85) { // 4 box coords + 1 confidence + 80 classes
                continue
            }

            val x = detection[0]
            val y = detection[1]
            val w = detection[2]
            val h = detection[3]
            val confidence = detection[4]

            if (confidence > 0.5f) { // Confidence threshold
                var maxClassScore = 0f
                var classIndex = -1
                // Start from index 5 to check class scores
                for (j in 5 until detection.size) {
                    if (detection[j] > maxClassScore) {
                        maxClassScore = detection[j]
                        classIndex = j - 5
                    }
                }

                if (maxClassScore > 0.5f) { // Class score threshold
                    val x1 = (x - w / 2) * originalWidth
                    val y1 = (y - h / 2) * originalHeight
                    val x2 = (x + w / 2) * originalWidth
                    val y2 = (y + h / 2) * originalHeight

                    val label = labels.getOrElse(classIndex) { "Unknown" }
                    detections.add(DetectionResult(RectF(x1, y1, x2, y2), "$label, ${String.format(Locale.US, "%.2f", confidence)}"))
                }
            }
        }
        return nonMaxSuppression(detections)
    }

    private fun nonMaxSuppression(detections: List<DetectionResult>, iouThreshold: Float = 0.45f): List<DetectionResult> {
        val sortedDetections = detections.sortedByDescending { 
            try {
                it.text.substringAfterLast(", ").toFloat()
            } catch (e: NumberFormatException) {
                0f
            }
        }
        val finalDetections = mutableListOf<DetectionResult>()

        for (detection in sortedDetections) {
            var shouldAdd = true
            for (finalDetection in finalDetections) {
                if (calculateIoU(detection.boundingBox, finalDetection.boundingBox) > iouThreshold) {
                    shouldAdd = false
                    break
                }
            }
            if (shouldAdd) {
                finalDetections.add(detection)
            }
        }
        return finalDetections
    }

    private fun calculateIoU(box1: RectF, box2: RectF): Float {
        val x1 = maxOf(box1.left, box2.left)
        val y1 = maxOf(box1.top, box2.top)
        val x2 = minOf(box1.right, box2.right)
        val y2 = minOf(box1.bottom, box2.bottom)

        val intersectionArea = maxOf(0f, x2 - x1) * maxOf(0f, y2 - y1)
        val box1Area = (box1.right - box1.left) * (box1.bottom - box1.top)
        val box2Area = (box2.right - box2.left) * (box2.bottom - box2.top)
        val unionArea = box1Area + box2Area - intersectionArea

        return if (unionArea == 0f) 0f else intersectionArea / unionArea
    }

    data class DetectionResult(val boundingBox: RectF, val text: String)
}