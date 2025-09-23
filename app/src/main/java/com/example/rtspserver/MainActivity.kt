package com.example.rtspserver

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.pedro.encoder.input.video.CameraOpenException
import com.pedro.library.rtsp.RtspCamera2
import com.pedro.rtspserver.util.ConnectCheckerRtsp
import com.pedro.encoder.input.video.Camera2View
import java.net.Inet4Address
import java.net.NetworkInterface

class MainActivity : AppCompatActivity(), ConnectCheckerRtsp {

    private lateinit var rtspCamera2: RtspCamera2
    private lateinit var startStopButton: Button
    private lateinit var rtspUrlTextView: TextView

    private val permissions = mutableListOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.INTERNET
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)

        val camera2View = findViewById<Camera2View>(R.id.surfaceView)
        startStopButton = findViewById(R.id.b_start_stop)
        rtspUrlTextView = findViewById(R.id.tv_url)

        rtspCamera2 = RtspCamera2(camera2View, this)

        startStopButton.setOnClickListener {
            if (!hasPermissions()) {
                requestPermissions()
            } else {
                toggleStream()
            }
        }
    }

    private fun hasPermissions(): Boolean {
        return permissions.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(this, permissions, 1)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            toggleStream()
        } else {
            Toast.makeText(this, "Permissions required to stream.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleStream() {
        if (!rtspCamera2.isStreaming) {
            try {
                if (rtspCamera2.prepareAudio() && rtspCamera2.prepareVideo()) {
                    rtspCamera2.startStream(getRtspUrl())
                } else {
                    Toast.makeText(this, "Error preparing stream", Toast.LENGTH_SHORT).show()
                }
            } catch (e: CameraOpenException) {
                Toast.makeText(this, "Error opening camera: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            rtspCamera2.stopStream()
        }
        updateUI()
    }

    private fun updateUI() {
        runOnUiThread {
            if (rtspCamera2.isStreaming) {
                startStopButton.text = "Stop Stream"
                rtspUrlTextView.text = getRtspUrl()
            } else {
                startStopButton.text = "Start Stream"
                rtspUrlTextView.text = ""
            }
        }
    }

    private fun getRtspUrl(): String {
        val ip = getLocalIpAddress()
        return "rtsp://$ip:1935"
    }

    private fun getLocalIpAddress(): String? {
        try {
            val en = NetworkInterface.getNetworkInterfaces()
            while (en.hasMoreElements()) {
                val intf = en.nextElement()
                val enumIpAddr = intf.inetAddresses
                while (enumIpAddr.hasMoreElements()) {
                    val inetAddress = enumIpAddr.nextElement()
                    if (!inetAddress.isLoopbackAddress && inetAddress is Inet4Address) {
                        return inetAddress.hostAddress
                    }
                }
            }
        } catch (ex: SocketException) {
            ex.printStackTrace()
        }
        return null
    }

    // ConnectCheckerRtsp implementation
    override fun onAuthErrorRtsp() {
        runOnUiThread {
            Toast.makeText(this, "Auth error", Toast.LENGTH_SHORT).show()
            rtspCamera2.stopStream()
            updateUI()
        }
    }

    override fun onAuthSuccessRtsp() {
        runOnUiThread { Toast.makeText(this, "Auth success", Toast.LENGTH_SHORT).show() }
    }

    override fun onConnectionFailedRtsp(reason: String) {
        runOnUiThread {
            Toast.makeText(this, "Connection failed: $reason", Toast.LENGTH_SHORT).show()
            rtspCamera2.stopStream()
            updateUI()
        }
    }

    override fun onConnectionSuccessRtsp() {
        runOnUiThread { Toast.makeText(this, "Connection success", Toast.LENGTH_SHORT).show() }
    }

    override fun onNewBitrateRtsp(bitrate: Long) {
    }

    override fun onDisconnectRtsp() {
        runOnUiThread {
            Toast.makeText(this, "Disconnected", Toast.LENGTH_SHORT).show()
            updateUI()
        }
    }
}
