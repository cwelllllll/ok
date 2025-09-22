package com.example.frigatecamerappv11

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.os.Build
import android.os.Bundle
import android.view.TextureView
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.video.CameraHelper
import com.pedro.encoder.input.video.CameraOpenException
import com.pedro.library.view.AutoFitTextureView
import com.pedro.rtspserver.RtspServerCamera1
import com.pedro.rtspserver.server.ClientListener
import com.pedro.rtspserver.server.ServerClient

class MainActivity : AppCompatActivity(), ConnectChecker, ClientListener, TextureView.SurfaceTextureListener {

    private lateinit var rtspServerCamera1: RtspServerCamera1
    private lateinit var surfaceView: AutoFitTextureView
    private lateinit var bStartStop: Button
    private lateinit var bSwitchCamera: Button
    private lateinit var tvUrl: TextView

    private val permissions = arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
    private val PERMISSIONS_REQUEST_CODE = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)

        surfaceView = findViewById(R.id.surfaceView)
        bStartStop = findViewById(R.id.b_start_stop)
        bSwitchCamera = findViewById(R.id.switch_camera)
        tvUrl = findViewById(R.id.tv_url)

        if (!hasPermissions(this, *permissions)) {
            ActivityCompat.requestPermissions(this, permissions, PERMISSIONS_REQUEST_CODE)
        } else {
            setupRtsp()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                setupRtsp()
            } else {
                toast("Permissions not granted")
                finish()
            }
        }
    }

    private fun setupRtsp() {
        rtspServerCamera1 = RtspServerCamera1(surfaceView, this, 1935)
        rtspServerCamera1.streamClient.setClientListener(this)
        surfaceView.surfaceTextureListener = this

        bStartStop.setOnClickListener {
            if (!rtspServerCamera1.isStreaming) {
                if (rtspServerCamera1.prepareAudio() && rtspServerCamera1.prepareVideo()) {
                    bStartStop.text = "Stop"
                    rtspServerCamera1.startStream()
                    tvUrl.text = rtspServerCamera1.streamClient.getEndPointConnection()
                } else {
                    toast("Error preparing stream, This device cant do it")
                }
            } else {
                bStartStop.text = "Start"
                rtspServerCamera1.stopStream()
                tvUrl.text = ""
            }
        }

        bSwitchCamera.setOnClickListener {
            try {
                rtspServerCamera1.switchCamera()
            } catch (e: CameraOpenException) {
                toast(e.message ?: "Unknown camera error")
            }
        }
    }

    private fun hasPermissions(activity: AppCompatActivity, vararg permissions: String): Boolean = permissions.all {
        ActivityCompat.checkSelfPermission(activity, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }

    private fun adaptPreview() {
        val isPortrait = CameraHelper.isPortrait(this)
        val w = if (isPortrait) rtspServerCamera1.streamHeight else rtspServerCamera1.streamWidth
        val h = if (isPortrait) rtspServerCamera1.streamWidth else rtspServerCamera1.streamHeight
        surfaceView.setAspectRatio(w, h)
    }

    override fun onConnectionSuccess() {
        toast("Connection success")
    }

    override fun onConnectionFailed(reason: String) {
        toast("Connection failed: $reason")
        rtspServerCamera1.stopStream()
        bStartStop.text = "Start"
    }

    override fun onConnectionStarted(url: String) {
    }

    override fun onDisconnect() {
        toast("Disconnected")
    }

    override fun onAuthError() {
        toast("Auth error")
    }

    override fun onAuthSuccess() {
        toast("Auth success")
    }

    override fun onClientConnected(client: ServerClient) {
        toast("Client connected: ${client.address}")
    }

    override fun onClientDisconnected(client: ServerClient) {
        toast("Client disconnected: ${client.address}")
    }

    override fun onClientNewBitrate(bitrate: Long, client: ServerClient) {}

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        if (!rtspServerCamera1.isOnPreview) {
            rtspServerCamera1.startPreview()
            adaptPreview()
        }
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        if (rtspServerCamera1.isStreaming) {
            rtspServerCamera1.stopStream()
            bStartStop.text = "Start"
        }
        if (rtspServerCamera1.isOnPreview) {
            rtspServerCamera1.stopPreview()
        }
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
    }
}
