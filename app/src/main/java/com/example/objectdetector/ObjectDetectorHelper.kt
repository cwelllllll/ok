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
    private val objectTracker = ObjectTracker()

    init {
        labels = FileUtil.loadLabels(context, LABELS_PATH)
        setupObjectDetector()
    }

    private fun setupObjectDetector() {
        try {
            val interpreterOptions = Interpreter.Options().apply {
                setNumThreads(numThreads)
                when (currentDelegate) {
                    DELEGATE_GPU -> if (CompatibilityList().isDelegateSupportedOnThisDevice) addDelegate(GpuDelegate())
                    DELEGATE_NNAPI -> addDelegate(NnApiDelegate())
                    DELEGATE_CPU -> { /* Default */ }
                }
            }
            interpreter = Interpreter(FileUtil.loadMappedFile(context, MODEL_NAME), interpreterOptions)
        } catch (e: Exception) {
            objectDetectorListener?.onError("TFLite failed to load model: ${e.message}")
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

        // YOLOv8 output shape is [1, 84, 8400] where 84=4+80 classes
        val outputBuffer = TensorBuffer.createFixedSize(intArrayOf(1, labels.size + 4, OUTPUT_ELEMENTS), DataType.FLOAT32)
        interpreter?.run(processedImage.buffer, outputBuffer.buffer.rewind())

        val rawDetections = processYoloOutput(outputBuffer.floatArray)
        val trackedObjects = objectTracker.update(rawDetections)

        objectDetectorListener?.onResults(trackedObjects, imageBitmap.height, imageBitmap.width)
    }

    // This is the corrected YOLO output processing logic
    private fun processYoloOutput(output: FloatArray): List<DetectionResult> {
        val detections = mutableListOf<DetectionResult>()
        val numClasses = labels.size

        for (i in 0 until OUTPUT_ELEMENTS) {
            // Direct indexing without transposition
            val cx = output[i]
            val cy = output[i + OUTPUT_ELEMENTS]
            val w = output[i + 2 * OUTPUT_ELEMENTS]
            val h = output[i + 3 * OUTPUT_ELEMENTS]

            var maxScore = 0f
            var classIndex = -1
            for (j in 0 until numClasses) {
                // Class scores start after the 4 bounding box coordinates
                val score = output[i + (4 + j) * OUTPUT_ELEMENTS]
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
                val box = RectF(x1, y1, x2, y2)
                if (classIndex != -1) {
                     detections.add(DetectionResult(box, labels[classIndex], maxScore))
                }
            }
        }
        return detections
    }

    // This is a data class that our ObjectTracker will use.
    data class DetectionResult(val boundingBox: RectF, val label: String, val score: Float)

    interface DetectorListener {
        fun onError(error: String)
        fun onResults(results: List<ObjectTracker.TrackedObject>, imageHeight: Int, imageWidth: Int)
    }

    companion object {
        const val DELEGATE_CPU = 0
        const val DELEGATE_GPU = 1
        const val DELEGATE_NNAPI = 2
        const val MODEL_NAME = "yolo11s_float32.tflite" // Corrected model name
        const val LABELS_PATH = "labels.txt"
        const val INPUT_SIZE = 640
        const val OUTPUT_ELEMENTS = 8400
        private const val TAG = "ObjectDetectorHelper"
    }
}