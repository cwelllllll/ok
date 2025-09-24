package com.example.rtspserver

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.opengl.GLES20
import android.opengl.GLUtils
import com.pedro.encoder.input.gl.render.filters.BaseFilterRender
import com.pedro.encoder.utils.gl.GlUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class OverlayRender(private val context: Context) : BaseFilterRender() {

    private val paint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 8.0f
        textSize = 60.0f
    }

    private var program = -1
    private var aPosition = -1
    private var uMVPMatrix = -1
    private var uSTMatrix = -1
    private var aTextureCoord = -1
    private var uAlpha = -1

    private lateinit var vertexBuffer: FloatBuffer
    private lateinit var uvBuffer: FloatBuffer

    private var detectionResults: List<ObjectDetector.DetectionResult>? = null
    private var overlayBitmap: Bitmap? = null
    private var overlayTextureId = -1
    private var isEnabled = false

    private val squareVertexData = floatArrayOf(
        -1f, -1f, 0f, 1f, -1f, 0f, -1f, 1f, 0f, 1f, 1f, 0f
    )
    private val squareVertexTexture = floatArrayOf(
        0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f
    )

    private val ALPHA_FRAGMENT_SHADER = """
        precision mediump float;
        varying vec2 vTextureCoord;
        uniform sampler2D sTexture;
        uniform float uAlpha;
        void main() {
            gl_FragColor = texture2D(sTexture, vTextureCoord);
            gl_FragColor.a *= uAlpha;
        }
    """

    override fun initGlFilter(context: Context) {
        val vertexShader = GlUtil.getStringFromRaw(context, com.pedro.encoder.R.raw.simple_vertex)
        program = GlUtil.createProgram(vertexShader, ALPHA_FRAGMENT_SHADER)
        aPosition = GLES20.glGetAttribLocation(program, "aPosition")
        uMVPMatrix = GLES20.glGetUniformLocation(program, "uMVPMatrix")
        uSTMatrix = GLES20.glGetUniformLocation(program, "uSTMatrix")
        aTextureCoord = GLES20.glGetAttribLocation(program, "aTextureCoord")
        uAlpha = GLES20.glGetUniformLocation(program, "uAlpha")

        vertexBuffer = ByteBuffer.allocateDirect(squareVertexData.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        vertexBuffer.put(squareVertexData).position(0)
        uvBuffer = ByteBuffer.allocateDirect(squareVertexTexture.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        uvBuffer.put(squareVertexTexture).position(0)
    }

    fun setDetectionResults(results: List<ObjectDetector.DetectionResult>) {
        this.detectionResults = results
    }

    override fun drawFilter() {
        if (isEnabled) {
            detectionResults?.let { results ->
                if (overlayBitmap == null || overlayBitmap!!.width != width || overlayBitmap!!.height != height) {
                    overlayBitmap?.recycle()
                    overlayBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                }
                overlayBitmap?.eraseColor(Color.TRANSPARENT)
                val canvas = Canvas(overlayBitmap!!)
                results.forEach { result ->
                    canvas.drawRect(result.boundingBox, paint)
                    canvas.drawText(result.text, result.boundingBox.left, result.boundingBox.top - 10, paint)
                }

                if (overlayTextureId == -1) {
                    val textures = IntArray(1)
                    GLES20.glGenTextures(1, textures, 0)
                    overlayTextureId = textures[0]
                }

                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, overlayTextureId)
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, overlayBitmap, 0)

                // Enable blending
                GLES20.glEnable(GLES20.GL_BLEND)
                GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

                // Draw the overlay
                GLES20.glUseProgram(program)
                GLES20.glUniformMatrix4fv(uMVPMatrix, 1, false, MVPMatrix, 0)
                GLES20.glUniformMatrix4fv(uSTMatrix, 1, false, STMatrix, 0)
                GLES20.glUniform1f(uAlpha, 1.0f)
                GLES20.glEnableVertexAttribArray(aPosition)
                GLES20.glVertexAttribPointer(aPosition, 3, GLES20.GL_FLOAT, false, 12, vertexBuffer)
                GLES20.glEnableVertexAttribArray(aTextureCoord)
                GLES20.glVertexAttribPointer(aTextureCoord, 2, GLES20.GL_FLOAT, false, 8, uvBuffer)
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, overlayTextureId)
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

                // Disable blending
                GLES20.glDisable(GLES20.GL_BLEND)
            }
        }
    }

    override fun release() {
        GLES20.glDeleteProgram(program)
        overlayBitmap?.recycle()
        if (overlayTextureId != -1) {
            val textures = intArrayOf(overlayTextureId)
            GLES20.glDeleteTextures(1, textures, 0)
        }
    }

    override fun disableResources() {
        release()
    }

    fun isEnabled(): Boolean = isEnabled

    fun setEnabled(enabled: Boolean) {
        this.isEnabled = enabled
    }
}
