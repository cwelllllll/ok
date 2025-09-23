package com.example.rtspserver;

import android.Manifest;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import net.majorkernelpanic.streaming.Session;
import net.majorkernelpanic.streaming.SessionBuilder;
import net.majorkernelpanic.streaming.audio.AudioQuality;
import net.majorkernelpanic.streaming.gl.SurfaceView;
import net.majorkernelpanic.streaming.rtsp.RtspServer;
import net.majorkernelpanic.streaming.video.VideoQuality;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

public class MainActivity extends AppCompatActivity implements Session.Callback, SurfaceHolder.Callback {

    private static final String TAG = "MainActivity";
    private static final int PERMISSIONS_REQUEST_CODE = 101;

    private SurfaceView mSurfaceView;
    private Button mToggleButton;
    private TextView mUrlTextView;
    private Session mSession;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        mSurfaceView = findViewById(R.id.surface);
        mToggleButton = findViewById(R.id.start_stop_button);
        mUrlTextView = findViewById(R.id.rtsp_url);

        // This is the core of the fix: The session is created and configured once in onCreate.
        mSession = SessionBuilder.getInstance()
                .setCallback(this)
                .setSurfaceView(mSurfaceView)
                .setPreviewOrientation(90)
                .setContext(getApplicationContext())
                .setAudioEncoder(SessionBuilder.AUDIO_AAC)
                .setAudioQuality(new AudioQuality(8000, 16000))
                .setVideoEncoder(SessionBuilder.VIDEO_H264)
                .setVideoQuality(new VideoQuality(640, 480, 20, 1000000))
                .build();

        mSurfaceView.getHolder().addCallback(this);

        mToggleButton.setOnClickListener(v -> {
            toggleStreaming();
        });

        // Start the RTSP server
        this.startService(new Intent(this, RtspServer.class));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (checkPermissions()) {
            // Permissions are granted, so we can resume the session.
            mSession.startPreview();
            updateUI();
        } else {
            requestPermissions();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Make sure to stop the preview and release the camera
        if (mSession.isStreaming()) {
            mSession.stop();
        }
        mSession.stopPreview();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Release the session and stop the server
        mSession.release();
        this.stopService(new Intent(this, RtspServer.class));
    }

    private void toggleStreaming() {
        if (!mSession.isStreaming()) {
            // Start streaming
            mSession.start();
        } else {
            // Stop streaming
            mSession.stop();
        }
        updateUI();
    }

    private void updateUI() {
        if (mSession.isStreaming()) {
            mToggleButton.setText("Stop");
            mUrlTextView.setText(getRtspUrl());
        } else {
            mToggleButton.setText("Start");
            mUrlTextView.setText("");
        }
    }

    private String getRtspUrl() {
        return "rtsp://" + getLocalIpAddress() + ":8086";
    }

    private String getLocalIpAddress() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address) {
                        return inetAddress.getHostAddress();
                    }
                }
            }
        } catch (SocketException e) {
            Log.e(TAG, "Error getting IP address", e);
        }
        return "127.0.0.1";
    }

    private boolean checkPermissions() {
        String[] permissions = {Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO};
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO},
                PERMISSIONS_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            if (!checkPermissions()) {
                Toast.makeText(this, "Permissions are required to run the app.", Toast.LENGTH_LONG).show();
                finish();
            } else {
                // Permissions granted, we can now start the preview
                mSession.startPreview();
                updateUI();
            }
        }
    }

    // Session.Callback methods
    @Override
    public void onBitrateUpdate(long bitrate) {}

    @Override
    public void onSessionError(int reason, int streamType, Exception e) {
        Log.e(TAG, "Session error: " + reason + ", " + streamType, e);
        Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        updateUI();
    }

    @Override
    public void onPreviewStarted() {
        Log.d(TAG, "Preview started.");
    }

    @Override
    public void onSessionConfigured() {
        Log.d(TAG, "Session configured.");
    }

    @Override
    public void onSessionStarted() {
        Log.d(TAG, "Session started.");
        updateUI();
    }

    @Override
    public void onSessionStopped() {
        Log.d(TAG, "Session stopped.");
        updateUI();
    }

    // SurfaceHolder.Callback methods
    @Override
    public void surfaceCreated(@NonNull SurfaceHolder holder) {
        Log.d(TAG, "Surface created.");
        // The session is already created, so we just need to start the preview
        mSession.startPreview();
    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {}

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
        Log.d(TAG, "Surface destroyed.");
        // The lifecycle methods (onPause) will handle stopping the preview.
    }
}
