package com.example.rtspserver

import android.content.Context
import android.graphics.Bitmap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ObjectDetectorProcessor(context: Context) {

    private val objectDetector = ObjectDetector(context, "yolov7.tflite", "labels.txt")
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private var listener: ((List<ObjectDetector.DetectionResult>) -> Unit)? = null

    fun setListener(listener: (List<ObjectDetector.DetectionResult>) -> Unit) {
        this.listener = listener
    }

    fun processBitmap(bitmap: Bitmap) {
        executor.submit {
            val results = objectDetector.detect(bitmap)
            listener?.invoke(results)
            bitmap.recycle()
        }
    }

    fun stop() {
        executor.shutdown()
    }
}