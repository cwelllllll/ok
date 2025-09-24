package com.example.rtspserver

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.opengl.GLES20
import com.pedro.encoder.input.gl.render.filters.BaseFilterRender
import java.nio.ByteBuffer

class ObjectDetectionFilter(context: Context) : BaseFilterRender() {

    var listener: ((Bitmap) -> Unit)? = null
    private var isEnabled = false

    override fun initGlFilter(context: Context) {
        // No GL resources to initialize
    }

    override fun drawFilter() {
        if (isEnabled) {
            // Read the pixels from the framebuffer and send to the listener
            val upsideDownBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val buffer = ByteBuffer.allocate(width * height * 4)
            GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer)
            upsideDownBitmap.copyPixelsFromBuffer(buffer)

            val matrix = Matrix().apply { preScale(1.0f, -1.0f) }
            val uprightBitmap = Bitmap.createBitmap(upsideDownBitmap, 0, 0, width, height, matrix, false)
            upsideDownBitmap.recycle()

            listener?.invoke(uprightBitmap)
        }
    }

    override fun release() {
        // No GL resources to release
    }

    override fun disableResources() {
        release()
    }

    fun isEnabled(): Boolean = isEnabled

    fun setEnabled(enabled: Boolean) {
        this.isEnabled = enabled
    }
}
