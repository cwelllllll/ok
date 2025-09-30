package com.example.objectdetector

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import androidx.camera.core.ImageProxy
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.Rot90Op
import org.tensorflow.lite.task.core.BaseOptions
import org.tensorflow.lite.task.vision.detector.Detection
import org.tensorflow.lite.task.vision.detector.ObjectDetector

class ObjectDetectorHelper(
    var threshold: Float = 0.5f,
    var numThreads: Int = 2,
    var maxResults: Int = 10,
    var currentDelegate: Int = 0,
    val context: Context,
    val objectDetectorListener: DetectorListener?
) {

    private var objectDetector: ObjectDetector? = null
    private val objectTracker = ObjectTracker()

    init {
        setupObjectDetector()
    }

    private fun setupObjectDetector() {
        try {
            val baseOptionsBuilder = BaseOptions.builder().setNumThreads(numThreads)
            when (currentDelegate) {
                DELEGATE_GPU -> baseOptionsBuilder.useGpu()
                DELEGATE_NNAPI -> baseOptionsBuilder.useNnapi()
                DELEGATE_CPU -> { /* Default */ }
            }

            val optionsBuilder =
                ObjectDetector.ObjectDetectorOptions.builder()
                    .setBaseOptions(baseOptionsBuilder.build())
                    .setScoreThreshold(threshold)
                    .setMaxResults(maxResults)

            objectDetector = ObjectDetector.createFromFileAndOptions(context, MODEL_NAME, optionsBuilder.build())

        } catch (e: Exception) {
            objectDetectorListener?.onError("TFLite model failed to load: ${e.message}")
            Log.e(TAG, "TFLite model failed to load", e)
        }
    }

    fun detect(imageProxy: ImageProxy) {
        if (objectDetector == null) {
            imageProxy.close()
            return
        }

        val imageBitmap = imageProxy.toBitmap()
        val rotation = imageProxy.imageInfo.rotationDegrees
        imageProxy.close()

        // The Task Library's ObjectDetector handles normalization and resizing internally.
        // We just need to handle the rotation manually before passing the image.
        val imageProcessor = ImageProcessor.Builder()
            .add(Rot90Op(-rotation / 90))
            .build()

        val tensorImage = imageProcessor.process(TensorImage.fromBitmap(imageBitmap))

        val results: List<Detection>? = objectDetector?.detect(tensorImage)

        val detectionResults = results?.map {
            DetectionResult(it.boundingBox, it.categories.first().label, it.categories.first().score)
        } ?: emptyList()

        val trackedObjects = objectTracker.update(detectionResults)

        objectDetectorListener?.onResults(trackedObjects, imageBitmap.height, imageBitmap.width)
    }

    data class DetectionResult(val boundingBox: RectF, val label: String, val score: Float)

    interface DetectorListener {
        fun onError(error: String)
        fun onResults(results: List<ObjectTracker.TrackedObject>, imageHeight: Int, imageWidth: Int)
    }

    companion object {
        const val DELEGATE_CPU = 0
        const val DELEGATE_GPU = 1
        const val DELEGATE_NNAPI = 2
        const val MODEL_NAME = "yolo11s_float32.tflite"
        const val INPUT_SIZE = 640
        private const val TAG = "ObjectDetectorHelper"
    }
}