package com.example.rtspserver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.util.Size
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.pedro.common.ConnectChecker
import com.pedro.library.view.OpenGlView
import com.pedro.rtspserver.RtspServerCamera2
import java.net.Inet4Address
import java.net.NetworkInterface

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class RtspService : Service(), ConnectChecker {

    private val binder = LocalBinder()
    private lateinit var rtspServerCamera2: RtspServerCamera2
    private val notificationId = 123456
    private val channelId = "rtsp_server_channel"

    // AI components
    private lateinit var objectDetectionFilter: ObjectDetectionFilter
    private lateinit var objectDetectorProcessor: ObjectDetectorProcessor
    private lateinit var overlayRender: OverlayRender
    private var aiEnabled = false
    private var filtersAdded = false

    // Video quality settings
    private var width = 1280
    private var height = 720
    private var bitrate = 2000 * 1024
    private var fps = 30
    private var rotation = 0

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("RTSP service is running")
            .build()
        startForeground(notificationId, notification)

        rtspServerCamera2 = RtspServerCamera2(applicationContext, this, 1935)
        objectDetectionFilter = ObjectDetectionFilter(applicationContext)
        objectDetectorProcessor = ObjectDetectorProcessor(applicationContext)
        overlayRender = OverlayRender(applicationContext)

        objectDetectorProcessor.setListener {
            overlayRender.setDetectionResults(it)
        }
        objectDetectionFilter.listener = {
            objectDetectorProcessor.processBitmap(it)
        }
    }

    inner class LocalBinder : Binder() {
        fun getService(): RtspService = this@RtspService
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("RtspService", "Service started")
        return START_STICKY
    }

    fun setView(openGlView: OpenGlView) {
        rtspServerCamera2.replaceView(openGlView)
    }

    fun clearView() {
        rtspServerCamera2.replaceView(applicationContext)
    }

    fun startPreview() {
        if (!filtersAdded) {
            rtspServerCamera2.glInterface.addFilter(objectDetectionFilter)
            rtspServerCamera2.glInterface.addFilter(overlayRender)
            objectDetectionFilter.setEnabled(false)
            overlayRender.setEnabled(false)
            filtersAdded = true
        }
        rtspServerCamera2.startPreview()
    }

    fun stopPreview() {
        rtspServerCamera2.stopPreview()
    }

    fun startStream(rotation: Int) {
        this.rotation = rotation
        if (!rtspServerCamera2.isStreaming) {
            if (rtspServerCamera2.prepareAudio() && rtspServerCamera2.prepareVideo(width, height, fps, bitrate, 2, rotation)) {
                val notification = NotificationCompat.Builder(this, channelId)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle(getString(R.string.app_name))
                    .setContentText(getString(R.string.streaming_in_progress))
                    .build()
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(notificationId, notification)
                rtspServerCamera2.startStream()
            }
        }
    }

    fun stopStream() {
        if (rtspServerCamera2.isStreaming) {
            rtspServerCamera2.stopStream()
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    fun isStreaming(): Boolean = rtspServerCamera2.isStreaming

    fun getResolutions(): List<Size> {
        return rtspServerCamera2.resolutionsBack
    }

    fun getSupportedFps(): List<Int> {
        return listOf(15, 20, 24, 30)
    }

    fun setVideoQuality(width: Int, height: Int, bitrate: Int, fps: Int) {
        this.width = width
        this.height = height
        this.bitrate = bitrate
        this.fps = fps
        if (isStreaming()) {
            stopStream()
            startStream(this.rotation)
        }
    }

    fun toggleAi() {
        aiEnabled = !aiEnabled
        objectDetectionFilter.setEnabled(aiEnabled)
        overlayRender.setEnabled(aiEnabled)
    }

    fun isAiEnabled(): Boolean = aiEnabled

    fun getUrl(): String = "rtsp://${getIpAddress()}:1935"

    private fun getIpAddress(): String {
        try {
            val networkInterfaces = NetworkInterface.getNetworkInterfaces()
            while (networkInterfaces.hasMoreElements()) {
                val networkInterface = networkInterfaces.nextElement()
                val inetAddresses = networkInterface.inetAddresses
                while (inetAddresses.hasMoreElements()) {
                    val inetAddress = inetAddresses.nextElement()
                    if (!inetAddress.isLoopbackAddress && inetAddress is Inet4Address) {
                        return inetAddress.hostAddress ?: "0.0.0.0"
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("RtspService", "Error getting IP address", e)
        }
        return "0.0.0.0"
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.app_name)
            val descriptionText = getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    // ConnectChecker methods
    override fun onConnectionStarted(url: String) {}
    override fun onConnectionSuccess() {}
    override fun onConnectionFailed(reason: String) {}
    override fun onNewBitrate(bitrate: Long) {}
    override fun onDisconnect() {}
    override fun onAuthError() {}
    override fun onAuthSuccess() {}
}