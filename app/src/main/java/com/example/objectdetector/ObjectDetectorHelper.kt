package com.example.objectdetector

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import androidx.camera.core.ImageProxy
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.image.ops.Rot90Op
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.util.PriorityQueue

class ObjectDetectorHelper(
    var threshold: Float = 0.5f,
    var numThreads: Int = 2,
    var maxResults: Int = 5,
    var currentDelegate: Int = 0,
    val context: Context,
    val objectDetectorListener: DetectorListener?
) {

    private var interpreter: Interpreter? = null
    private var labels: List<String>

    init {
        labels = FileUtil.loadLabels(context, LABELS_PATH)
        setupObjectDetector()
    }

    private fun setupObjectDetector() {
        try {
            val interpreterOptions = Interpreter.Options().apply {
                setNumThreads(numThreads)
                when (currentDelegate) {
                    DELEGATE_GPU -> {
                        if (CompatibilityList().isDelegateSupportedOnThisDevice) {
                            addDelegate(GpuDelegate())
                        } else {
                            objectDetectorListener?.onError("GPU is not supported on this device")
                        }
                    }
                    DELEGATE_CPU -> { /* Default */ }
                }
            }
            interpreter = Interpreter(FileUtil.loadMappedFile(context, MODEL_NAME), interpreterOptions)
        } catch (e: Exception) {
            objectDetectorListener?.onError("TensorFlow Lite failed to load model: ${e.message}")
            Log.e(TAG, "TFLite failed to load model", e)
        }
    }

    fun detect(imageProxy: ImageProxy) {
        if (interpreter == null) return

        val imageBitmap = imageProxy.toBitmap()
        val rotation = imageProxy.imageInfo.rotationDegrees
        imageProxy.close()

        val imageProcessor = ImageProcessor.Builder()
            .add(Rot90Op(-rotation / 90))
            .add(ResizeOp(INPUT_SIZE, INPUT_SIZE, ResizeOp.ResizeMethod.BILINEAR))
            .add(NormalizeOp(0f, 255f)) // This is the crucial normalization step
            .build()

        val tensorImage = TensorImage(DataType.FLOAT32)
        tensorImage.load(imageBitmap)
        val processedImage = imageProcessor.process(tensorImage)

        val outputBuffer = TensorBuffer.createFixedSize(intArrayOf(1, OUTPUT_ELEMENTS, labels.size + 4), DataType.FLOAT32)
        interpreter?.run(processedImage.buffer, outputBuffer.buffer.rewind())

        val results = processYoloOutput(outputBuffer.floatArray, imageBitmap.width, imageBitmap.height)
        objectDetectorListener?.onResults(results, imageBitmap.height, imageBitmap.width)
    }

    private fun processYoloOutput(output: FloatArray, imageWidth: Int, imageHeight: Int): List<DetectionResult> {
        val boxes = mutableListOf<RectF>()
        val scores = mutableListOf<Float>()
        val classIndexes = mutableListOf<Int>()

        val xFactor = imageWidth / INPUT_SIZE.toFloat()
        val yFactor = imageHeight / INPUT_SIZE.toFloat()

        val numClasses = labels.size
        val numElementsPerDetection = numClasses + 4

        for (i in 0 until OUTPUT_ELEMENTS) {
            val baseIdx = i * numElementsPerDetection
            val cx = output[baseIdx]
            val cy = output[baseIdx + 1]
            val w = output[baseIdx + 2]
            val h = output[baseIdx + 3]

            var maxScore = 0f
            var classIndex = -1
            for (j in 0 until numClasses) {
                val score = output[baseIdx + 4 + j]
                if (score > maxScore) {
                    maxScore = score
                    classIndex = j
                }
            }

            if (maxScore > threshold) {
                val x1 = (cx - w / 2) * xFactor
                val y1 = (cy - h / 2) * yFactor
                val x2 = (cx + w / 2) * xFactor
                val y2 = (cy + h / 2) * yFactor
                boxes.add(RectF(x1, y1, x2, y2))
                scores.add(maxScore)
                classIndexes.add(classIndex)
            }
        }

        return nonMaxSuppression(boxes, scores, classIndexes)
    }

    private fun nonMaxSuppression(boxes: List<RectF>, scores: List<Float>, classIndexes: List<Int>): List<DetectionResult> {
        val pq = PriorityQueue<IndexedDetection>(scores.size, compareByDescending { it.score })
        for (i in boxes.indices) {
            pq.add(IndexedDetection(i, boxes[i], scores[i], classIndexes[i]))
        }

        val result = mutableListOf<DetectionResult>()
        while (pq.isNotEmpty()) {
            val best = pq.poll() ?: continue
            if (result.size >= maxResults) break

            val label = labels[best.classIndex]
            result.add(DetectionResult(best.box, "$label, ${String.format("%.2f", best.score)}"))

            val iterator = pq.iterator()
            while (iterator.hasNext()) {
                val next = iterator.next()
                if (iou(best.box, next.box) > IOU_THRESHOLD) {
                    iterator.remove()
                }
            }
        }
        return result
    }

    private fun iou(a: RectF, b: RectF): Float {
        val intersectionX = maxOf(a.left, b.left)
        val intersectionY = maxOf(a.top, b.top)
        val intersectionWidth = minOf(a.right, b.right) - intersectionX
        val intersectionHeight = minOf(a.bottom, b.bottom) - intersectionY

        if (intersectionWidth <= 0 || intersectionHeight <= 0) return 0f

        val intersectionArea = intersectionWidth * intersectionHeight
        val areaA = (a.right - a.left) * (a.bottom - a.top)
        val areaB = (b.right - b.left) * (b.bottom - a.top)

        return intersectionArea / (areaA + areaB - intersectionArea)
    }

    data class DetectionResult(val boundingBox: RectF, val text: String)
    private data class IndexedDetection(val index: Int, val box: RectF, val score: Float, val classIndex: Int)

    interface DetectorListener {
        fun onError(error: String)
        fun onResults(results: List<DetectionResult>, imageHeight: Int, imageWidth: Int)
    }

    companion object {
        const val DELEGATE_CPU = 0
        const val DELEGATE_GPU = 1
        const val MODEL_NAME = "yolo11n_float32.tflite"
        const val LABELS_PATH = "labels.txt"
        const val INPUT_SIZE = 640
        const val OUTPUT_ELEMENTS = 25200
        const val IOU_THRESHOLD = 0.5f
        private const val TAG = "ObjectDetectorHelper"
    }
}