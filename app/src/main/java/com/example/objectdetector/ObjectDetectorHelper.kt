package com.example.objectdetector

import android.content.Context
import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.Rot90Op
import org.tensorflow.lite.task.core.BaseOptions
import org.tensorflow.lite.task.vision.detector.Detection
import org.tensorflow.lite.task.vision.detector.ObjectDetector
import java.io.IOException
import android.util.Log

class ObjectDetectorHelper(
    var threshold: Float = 0.5f,
    var numThreads: Int = 2,
    var maxResults: Int = 3,
    var currentDelegate: Int = 0,
    val context: Context,
    val objectDetectorListener: DetectorListener?
) {

    private var objectDetector: ObjectDetector? = null

    init {
        setupObjectDetector()
    }

    private fun setupObjectDetector() {
        // Step 1: Check if the model file exists and is not empty.
        try {
            val assetManager = context.assets
            val modelFile = assetManager.openFd(MODEL_NAME)
            modelFile.close() // Close the FileDescriptor
            if (modelFile.length == 0L) {
                objectDetectorListener?.onError("Model file is empty. Please replace it with your model in app/src/main/assets/.")
                Log.e(TAG, "Model file is empty.")
                return
            }
        } catch (e: IOException) {
            objectDetectorListener?.onError("Model file not found. Please add a model file to app/src/main/assets/.")
            Log.e(TAG, "Model file not found: $MODEL_NAME", e)
            return
        }


        // Step 2: Create options for the detector.
        val optionsBuilder =
            ObjectDetector.ObjectDetectorOptions.builder()
                .setScoreThreshold(threshold)
                .setMaxResults(maxResults)

        val baseOptionsBuilder = BaseOptions.builder().setNumThreads(numThreads)
        when (currentDelegate) {
            DELEGATE_CPU -> {
                // Default
            }
            DELEGATE_GPU -> {
                if (CompatibilityList().isDelegateSupportedOnThisDevice) {
                    baseOptionsBuilder.useGpu()
                } else {
                    objectDetectorListener?.onError("GPU is not supported on this device")
                }
            }
            DELEGATE_NNAPI -> {
                baseOptionsBuilder.useNnapi()
            }
        }
        optionsBuilder.setBaseOptions(baseOptionsBuilder.build())

        // Step 3: Create the detector.
        try {
            objectDetector =
                ObjectDetector.createFromFileAndOptions(context, MODEL_NAME, optionsBuilder.build())
        } catch (e: IllegalStateException) {
            objectDetectorListener?.onError(
                "Object detector failed to initialize. See error logs for details"
            )
            Log.e(TAG, "TFLite failed to load model with error: " + e.message)
        }
    }

    fun detect(imageProxy: ImageProxy) {
        if (objectDetector == null) {
            imageProxy.close() // Close the proxy if detector is not ready
            return
        }

        // Correctly get rotation degrees before any conversion that might close the proxy
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees

        // Convert ImageProxy to Bitmap. The analyzer will close the imageProxy.
        val bitmap = imageProxy.toBitmap()
        imageProxy.close() // We are now responsible for closing the ImageProxy

        // Create an ImageProcessor with the correct rotation
        val imageProcessor =
            ImageProcessor.Builder()
                .add(Rot90Op(-rotationDegrees / 90))
                .build()

        val tensorImage = imageProcessor.process(TensorImage.fromBitmap(bitmap))

        val results = objectDetector?.detect(tensorImage)
        objectDetectorListener?.onResults(
            results,
            tensorImage.height,
            tensorImage.width
        )
    }

    interface DetectorListener {
        fun onError(error: String)
        fun onResults(
            results: MutableList<Detection>?,
            imageHeight: Int,
            imageWidth: Int
        )
    }

    companion object {
        const val DELEGATE_CPU = 0
        const val DELEGATE_GPU = 1
        const val DELEGATE_NNAPI = 2
        const val MODEL_NAME = "yolo11n_float32.tflite"

        private const val TAG = "ObjectDetectorHelper"
    }
}