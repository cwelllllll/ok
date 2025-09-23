package com.example.rtspserver

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.rtspserver.databinding.ActivityMainBinding
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.video.CameraOpenException
import com.pedro.library.rtsp.RtspCamera2
import java.net.SocketException

class MainActivity : AppCompatActivity(), ConnectChecker {

    private lateinit var rtspCamera2: RtspCamera2
    private lateinit var binding: ActivityMainBinding

    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            if (permissions.all { it.value }) {
                toggleStream()
            } else {
                Toast.makeText(this, "Permissions not granted.", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        rtspCamera2 = RtspCamera2(binding.surfaceView, this)

        binding.bStartStop.setOnClickListener {
            if (!hasPermissions()) {
                requestPermissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
            } else {
                toggleStream()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        binding.bStartStop.text = if (rtspCamera2.isStreaming) "Stop Stream" else "Start Stream"
    }

    override fun onPause() {
        super.onPause()
        if (rtspCamera2.isStreaming) {
            rtspCamera2.stopStream()
            binding.bStartStop.text = "Start Stream"
            binding.tvUrl.text = ""
        }
    }

    private fun hasPermissions(): Boolean {
        return arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO).all {
            ActivityCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun toggleStream() {
        try {
            val endpoint = "/live/pedro"
            if (!rtspCamera2.isStreaming) {
                if (rtspCamera2.prepareAudio() && rtspCamera2.prepareVideo()) {
                    binding.bStartStop.text = "Stop Stream"
                    rtspCamera2.startStream(endpoint)
                    binding.tvUrl.text = "rtsp://${rtspCamera2.serverIp}:${rtspCamera2.serverPort}${endpoint}"
                } else {
                    Toast.makeText(this, "Error preparing stream", Toast.LENGTH_SHORT).show()
                }
            } else {
                binding.bStartStop.text = "Start Stream"
                rtspCamera2.stopStream()
                binding.tvUrl.text = ""
            }
        } catch (e: CameraOpenException) {
            Toast.makeText(this, "Error opening camera: ${e.message}", Toast.LENGTH_SHORT).show()
        } catch (e: SocketException) {
            Toast.makeText(this, "Network error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onConnectionStarted(url: String) {
        // Called when server starts listening
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
}
