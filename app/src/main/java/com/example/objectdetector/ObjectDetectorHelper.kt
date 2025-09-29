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
import org.tensorflow.lite.nnapi.NnApiDelegate
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
    var maxResults: Int = 10,
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
                    DELEGATE_NNAPI -> addDelegate(NnApiDelegate())
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
            .add(NormalizeOp(0f, 255f))
            .build()

        val tensorImage = TensorImage(DataType.FLOAT32)
        tensorImage.load(imageBitmap)
        val processedImage = imageProcessor.process(tensorImage)

        val outputBuffer = TensorBuffer.createFixedSize(intArrayOf(1, labels.size + 4, OUTPUT_ELEMENTS), DataType.FLOAT32)
        interpreter?.run(processedImage.buffer, outputBuffer.buffer.rewind())

        val results = processYoloOutput(outputBuffer.floatArray)
        objectDetectorListener?.onResults(results, imageBitmap.height, imageBitmap.width)
    }

    private fun processYoloOutput(output: FloatArray): List<DetectionResult> {
        val detections = mutableListOf<DetectionResult>()

        // Transpose the output from [1, 84, 8400] to [8400, 84]
        val transposedOutput = FloatArray(OUTPUT_ELEMENTS * (labels.size + 4))
        for (i in 0 until OUTPUT_ELEMENTS) {
            for (j in 0 until labels.size + 4) {
                transposedOutput[i * (labels.size + 4) + j] = output[j * OUTPUT_ELEMENTS + i]
            }
        }

        for (i in 0 until OUTPUT_ELEMENTS) {
            val offset = i * (labels.size + 4)
            val cx = transposedOutput[offset]
            val cy = transposedOutput[offset + 1]
            val w = transposedOutput[offset + 2]
            val h = transposedOutput[offset + 3]

            var maxScore = 0f
            var classIndex = -1
            for (j in 0 until labels.size) {
                val score = transposedOutput[offset + 4 + j]
                if (score > maxScore) {
                    maxScore = score
                    classIndex = j
                }
            }

            if (maxScore > threshold) {
                val x1 = cx - w / 2
                val y1 = cy - h / 2
                val x2 = cx + w / 2
                val y2 = cy + h / 2
                // Bounding box is in model coordinates (0-640)
                val box = RectF(x1, y1, x2, y2)
                detections.add(DetectionResult(box, labels[classIndex], maxScore))
            }
        }

        return nonMaxSuppression(detections)
    }

    private fun nonMaxSuppression(detections: List<DetectionResult>): List<DetectionResult> {
        val sortedDetections = detections.sortedByDescending { it.score }
        val result = mutableListOf<DetectionResult>()

        for (detection in sortedDetections) {
            var shouldAdd = true
            for (existing in result) {
                if (iou(detection.boundingBox, existing.boundingBox) > IOU_THRESHOLD) {
                    shouldAdd = false
                    break
                }
            }
            if (shouldAdd) {
                result.add(detection)
            }
            if (result.size >= maxResults) {
                break
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
        val areaB = (b.right - b.left) * (b.bottom - b.top)

        return intersectionArea / (areaA + areaB - intersectionArea)
    }

    data class DetectionResult(val boundingBox: RectF, val label: String, val score: Float)

    interface DetectorListener {
        fun onError(error: String)
        fun onResults(results: List<DetectionResult>, imageHeight: Int, imageWidth: Int)
    }

    companion object {
        const val DELEGATE_CPU = 0
        const val DELEGATE_GPU = 1
        const val DELEGATE_NNAPI = 2
        const val MODEL_NAME = "yolo11n_float32.tflite"
        const val LABELS_PATH = "labels.txt"
        const val INPUT_SIZE = 640
        const val OUTPUT_ELEMENTS = 8400 // For a 640x640 YOLOv8 model, this is the number of detection candidates
        const val IOU_THRESHOLD = 0.5f
        private const val TAG = "ObjectDetectorHelper"
    }
}