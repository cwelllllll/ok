package com.example.rtspserver

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.Surface
import android.view.SurfaceHolder
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.rtspserver.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity(), SurfaceHolder.Callback {

    private lateinit var binding: ActivityMainBinding
    private var rtspService: RtspService? = null
    private var isBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as RtspService.LocalBinder
            rtspService = binder.getService()
            isBound = true
            rtspService?.setView(binding.surfaceView)
            updateUi()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            rtspService = null
            isBound = false
        }
    }

    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            if (permissions.all { it.value }) {
                // Permissions granted, now we can start the preview
                rtspService?.startPreview()
            } else {
                Toast.makeText(this, getString(R.string.permissions_not_granted), Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.surfaceView.holder.addCallback(this)

        binding.bStartStop.setOnClickListener {
            if (isBound) {
                if (rtspService?.isStreaming() == true) {
                    rtspService?.stopStream()
                } else {
                    rtspService?.startStream(getDeviceRotation())
                }
                updateUi()
            }
        }

        binding.bSelectQuality.setOnClickListener {
            showResolutionDialog()
        }

        binding.bToggleAi.setOnClickListener {
            if (isBound) {
                rtspService?.toggleAi()
                updateUi()
            }
        }

        checkAndRequestPermissions()
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.CAMERA)
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            // Permissions are already granted
        }
    }

    private fun hasPermissions(): Boolean {
        val basePermissions = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        var allGranted = basePermissions.all { ActivityCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            allGranted = allGranted && ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        }
        return allGranted
    }

    private fun getDeviceRotation(): Int {
        val rotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display?.rotation
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.rotation
        }
        return when (rotation) {
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
    }

    private fun showResolutionDialog() {
        if (!isBound || rtspService == null) return

        val resolutions = rtspService!!.getResolutions()
        if (resolutions.isEmpty()) {
            Toast.makeText(this, "No resolutions available", Toast.LENGTH_SHORT).show()
            return
        }

        val resolutionItems = resolutions.map { "${it.width}x${it.height}" }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Select Resolution")
            .setItems(resolutionItems) { _, which ->
                val selectedResolution = resolutions[which]
                showFpsDialog(selectedResolution)
            }
            .show()
    }

    private fun showFpsDialog(selectedResolution: android.util.Size) {
        if (!isBound || rtspService == null) return

        val fpsOptions = rtspService!!.getSupportedFps()
        val fpsItems = fpsOptions.map { "$it FPS" }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Select FPS")
            .setItems(fpsItems) { _, which ->
                val selectedFps = fpsOptions[which]
                val newBitrate = (selectedResolution.width * selectedResolution.height * 2.5).toInt()
                rtspService?.setVideoQuality(selectedResolution.width, selectedResolution.height, newBitrate, selectedFps)
                Toast.makeText(this, "Quality set to ${selectedResolution.width}x${selectedResolution.height} @ $selectedFps FPS", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    override fun onResume() {
        super.onResume()
        Intent(this, RtspService::class.java).also { intent ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onPause() {
        super.onPause()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }

    private fun updateUi() {
        if (isBound && rtspService != null) {
            if (rtspService!!.isStreaming()) {
                binding.bStartStop.text = getString(R.string.stop_stream)
                binding.tvUrl.text = rtspService!!.getUrl()
            } else {
                binding.bStartStop.text = getString(R.string.start_stream)
                binding.tvUrl.text = ""
            }
            if (rtspService!!.isAiEnabled()) {
                binding.bToggleAi.text = getString(R.string.disable_ai)
            } else {
                binding.bToggleAi.text = getString(R.string.enable_ai)
            }
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        if (isBound && hasPermissions()) {
            rtspService?.startPreview()
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        // The library should handle surface changes.
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        if (isBound) {
            rtspService?.stopPreview()
        }
    }
}
