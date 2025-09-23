package com.example.rtspserver

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
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
    private lateinit var tvUrl: TextView
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
        tvUrl = findViewById(R.id.tv_url)

        rtspCamera2 = RtspCamera2(openGlView, this)

        button.setOnClickListener {
            if (!hasPermissions()) {
                requestPermissions()
            } else {
                toggleStream()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Update button text in case the user comes back to the app
        if (rtspCamera2.isStreaming) {
            button.text = "Stop Stream"
        } else {
            button.text = "Start Stream"
        }
    }

    override fun onPause() {
        super.onPause()
        // Stop stream if it is running to release the camera and microphone
        if (rtspCamera2.isStreaming) {
            rtspCamera2.stopStream()
            button.text = "Start Stream"
            tvUrl.text = ""
        }
    }

    private fun hasPermissions(): Boolean {
        return requiredPermissions.all {
            ActivityCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        if (requiredPermissions.any { shouldShowRequestPermissionRationale(it) }) {
            // Show an explanation to the user *asynchronously*
            Toast.makeText(this, "Camera and microphone permissions are required to stream.", Toast.LENGTH_LONG).show()
        }
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
                // If permission is permanently denied, guide the user to settings
                if (requiredPermissions.any { !shouldShowRequestPermissionRationale(it) }) {
                    Toast.makeText(this, "Permissions permanently denied. Please enable them in app settings.", Toast.LENGTH_LONG).show()
                    // Optionally, open app settings
                    // val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    // val uri = Uri.fromParts("package", packageName, null)
                    // intent.data = uri
                    // startActivity(intent)
                } else {
                    Toast.makeText(this, "Permissions not granted.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun toggleStream() {
        try {
            if (!rtspCamera2.isStreaming) {
                if (rtspCamera2.prepareAudio() && rtspCamera2.prepareVideo()) {
                    button.text = "Stop Stream"
                    rtspCamera2.startStream()
                    tvUrl.text = rtspCamera2.getEndPointConnection()
                } else {
                     Toast.makeText(this, "Error preparing stream", Toast.LENGTH_SHORT).show()
                }
            } else {
                button.text = "Start Stream"
                rtspCamera2.stopStream()
                tvUrl.text = ""
            }
        } catch (e: CameraOpenException) {
            Toast.makeText(this, "Error opening camera: ${e.message}", Toast.LENGTH_SHORT).show()
        } catch (e: SocketException) {
            Toast.makeText(this, "Network error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // ConnectCheckerRtsp callbacks
    override fun onConnectionStartedRtsp(rtspUrl: String) {
        // Not needed for this simple case
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
            tvUrl.text = ""
        }
    }

    override fun onNewBitrateRtsp(bitrate: Long) {
        // Not needed for this simple case
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
