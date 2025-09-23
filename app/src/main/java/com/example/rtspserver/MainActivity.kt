package com.example.rtspserver

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.SurfaceHolder
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.rtspserver.databinding.ActivityMainBinding
import com.pedro.common.ConnectChecker
import com.pedro.library.rtsp.RtspCamera2

class MainActivity : AppCompatActivity(), ConnectChecker, SurfaceHolder.Callback {

    private lateinit var rtspCamera2: RtspCamera2
    private lateinit var binding: ActivityMainBinding

    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            if (permissions.all { it.value }) {
                // Permissions granted, start the preview
                rtspCamera2.startPreview()
            } else {
                Toast.makeText(this, "Permissions not granted.", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        rtspCamera2 = RtspCamera2(binding.surfaceView, this)
        // Add the SurfaceHolder.Callback to manage the preview lifecycle
        binding.surfaceView.holder.addCallback(this)

        binding.bStartStop.setOnClickListener {
            if (!rtspCamera2.isStreaming) {
                if (rtspCamera2.prepareAudio() && rtspCamera2.prepareVideo()) {
                    binding.bStartStop.text = "Stop Stream"
                    rtspCamera2.startStream("/live/pedro")
                    // The URL will be set in the onConnectionStarted callback
                } else {
                    Toast.makeText(this, "Error preparing stream", Toast.LENGTH_SHORT).show()
                }
            } else {
                binding.bStartStop.text = "Start Stream"
                rtspCamera2.stopStream()
                binding.tvUrl.text = ""
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Stop the stream and preview when the activity is paused
        if (rtspCamera2.isStreaming) {
            rtspCamera2.stopStream()
            binding.bStartStop.text = "Start Stream"
            binding.tvUrl.text = ""
        }
        // This is critical to release the camera before the surface is destroyed
        if (rtspCamera2.isRecording || rtspCamera2.isOnPreview) {
            rtspCamera2.stopPreview()
        }
    }

    private fun hasPermissions(): Boolean {
        return arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO).all {
            ActivityCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    override fun onConnectionStarted(url: String) {
        runOnUiThread {
            binding.tvUrl.text = url
        }
    }

    override fun onConnectionSuccess() {
        runOnUiThread {
            Toast.makeText(this, "Connection success", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onConnectionFailed(reason: String) {
        runOnUiThread {
            Toast.makeText(this, "Connection failed: $reason", Toast.LENGTH_SHORT).show()
            rtspCamera2.stopStream()
            binding.bStartStop.text = "Start Stream"
            binding.tvUrl.text = ""
        }
    }

    override fun onNewBitrate(bitrate: Long) {
        // Called when bitrate changes
    }

    override fun onDisconnect() {
        runOnUiThread {
            Toast.makeText(this, "Disconnected", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onAuthError() {
        runOnUiThread {
            Toast.makeText(this, "Auth error", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onAuthSuccess() {
        runOnUiThread {
            Toast.makeText(this, "Auth success", Toast.LENGTH_SHORT).show()
        }
    }

    // SurfaceHolder.Callback implementation
    override fun surfaceCreated(holder: SurfaceHolder) {
        // When the surface is created, start the camera preview if permissions are granted
        if (hasPermissions()) {
            rtspCamera2.startPreview()
        } else {
            requestPermissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        // You can handle orientation changes here by restarting the preview.
        // This is also called on start up, so we need to check for permissions.
        rtspCamera2.stopPreview()
        if (hasPermissions()) {
            rtspCamera2.startPreview()
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        // When the surface is destroyed, we must stop the preview
        if (rtspCamera2.isRecording || rtspCamera2.isOnPreview) {
            rtspCamera2.stopPreview()
        }
    }
}
