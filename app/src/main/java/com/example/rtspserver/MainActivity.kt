package com.example.rtspserver

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.pedro.encoder.input.video.CameraOpenException
import com.pedro.library.rtsp.RtspCamera2
import com.pedro.library.view.OpenGlView
import com.pedro.rtsp.utils.ConnectCheckerRtsp
import java.net.SocketException

class MainActivity : AppCompatActivity(), ConnectCheckerRtsp, ActivityCompat.OnRequestPermissionsResultCallback {

    private lateinit var rtspCamera2: RtspCamera2
    private lateinit var button: Button
    private lateinit var openGlView: OpenGlView
    private val PERMISSIONS_REQUEST_CODE = 1

    private val requiredPermissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        openGlView = findViewById(R.id.surfaceView)
        button = findViewById(R.id.b_start_stop)

        rtspCamera2 = RtspCamera2(openGlView, this)

        button.setOnClickListener {
            if (!hasPermissions()) {
                requestPermissions()
            } else {
                toggleStream()
            }
        }
    }

    private fun hasPermissions(): Boolean {
        return requiredPermissions.all {
            ActivityCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(this, requiredPermissions, PERMISSIONS_REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                toggleStream()
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun toggleStream() {
        try {
            if (!rtspCamera2.isStreaming) {
                if (rtspCamera2.prepareAudio() && rtspCamera2.prepareVideo()) {
                    button.text = "Stop Stream"
                    rtspCamera2.startStream()
                    val endpoint = rtspCamera2.getEndPointConnection()
                    Toast.makeText(this, "RTSP URL: $endpoint", Toast.LENGTH_LONG).show()
                }
            } else {
                button.text = "Start Stream"
                rtspCamera2.stopStream()
            }
        } catch (e: CameraOpenException) {
            Toast.makeText(this, "Error opening camera: ${e.message}", Toast.LENGTH_SHORT).show()
        } catch (e: SocketException) {
            Toast.makeText(this, "Network error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onConnectionStartedRtsp(rtspUrl: String) {
        // Not used
    }

    override fun onConnectionSuccessRtsp() {
        runOnUiThread {
            Toast.makeText(this, "Connection success", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onConnectionFailedRtsp(reason: String) {
        runOnUiThread {
            Toast.makeText(this, "Connection failed: $reason", Toast.LENGTH_SHORT).show()
            rtspCamera2.stopStream()
            button.text = "Start Stream"
        }
    }

    override fun onNewBitrateRtsp(bitrate: Long) {
        // Not used
    }

    override fun onDisconnectRtsp() {
        runOnUiThread {
            Toast.makeText(this, "Disconnected", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onAuthErrorRtsp() {
        runOnUiThread {
            Toast.makeText(this, "Auth error", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onAuthSuccessRtsp() {
        runOnUiThread {
            Toast.makeText(this, "Auth success", Toast.LENGTH_SHORT).show()
        }
    }
}
