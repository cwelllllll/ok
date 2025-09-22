package com.example.frigatecamerappv7;

import net.majorkernelpanic.streaming.Session;
import net.majorkernelpanic.streaming.SessionBuilder;
import net.majorkernelpanic.streaming.gl.SurfaceView;
import net.majorkernelpanic.streaming.rtsp.RtspServer;
import net.majorkernelpanic.streaming.video.VideoQuality;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.format.Formatter;
import android.util.Log;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity implements Session.Callback {

    private static final String TAG = "MainActivity";
    private final String[] REQUIRED_PERMISSIONS = new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO};
    private static final int RTSP_PORT = 1234;

    private SurfaceView mSurfaceView;
    private TextView mUrlTextView;
    private Session mSession;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        mSurfaceView = findViewById(R.id.surface);
        mUrlTextView = findViewById(R.id.tv_url);

        // Check for permissions. If granted, start the server. Otherwise, request them.
        if (allPermissionsGranted()) {
            initializeRtsp();
        } else {
            requestPermissionsLauncher.launch(REQUIRED_PERMISSIONS);
        }
    }

    private void initializeRtsp() {
        // Sets the port of the RTSP server
        Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
        editor.putString(RtspServer.KEY_PORT, String.valueOf(RTSP_PORT));
        editor.commit();

        // Configures the SessionBuilder
        mSession = SessionBuilder.getInstance()
                .setSurfaceView(mSurfaceView)
                .setPreviewOrientation(90)
                .setContext(getApplicationContext())
                .setAudioEncoder(SessionBuilder.AUDIO_NONE)
                .setVideoEncoder(SessionBuilder.VIDEO_H264)
                .setVideoQuality(new VideoQuality(1280, 720, 30, 500000)) // Set a standard resolution
                .setCallback(this)
                .build();

        // Starts the RTSP server
        this.startService(new Intent(this, RtspServer.class));

        // Display the RTSP URL
        mUrlTextView.setText(getIpAddress());
    }

    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private final ActivityResultLauncher<String[]> requestPermissionsLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                if (Boolean.TRUE.equals(result.get(Manifest.permission.CAMERA)) && Boolean.TRUE.equals(result.get(Manifest.permission.RECORD_AUDIO))) {
                    // Permissions granted, initialize the server
                    initializeRtsp();
                } else {
                    // Permissions denied, show a message and close the app
                    Toast.makeText(this, "Permissions not granted. App will close.", Toast.LENGTH_LONG).show();
                    finish();
                }
            });

    @Override
    public void onResume() {
        super.onResume();
        if (mSession != null) {
            mSession.startPreview();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mSession != null) {
            mSession.stopPreview();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mSession != null) {
            mSession.release();
        }
        stopService(new Intent(this, RtspServer.class));
    }

    private String getIpAddress() {
        try {
            WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            return "rtsp://" + Formatter.formatIpAddress(wifiManager.getConnectionInfo().getIpAddress()) + ":" + RTSP_PORT;
        } catch (Exception e) {
            return "IP not available";
        }
    }

    @Override
    public void onBitrateUpdate(long bitrate) {
        Log.d(TAG, "Bitrate: " + bitrate);
    }

    @Override
    public void onSessionError(int reason, int streamType, Exception e) {
        Log.e(TAG, "Session error", e);
        runOnUiThread(() -> {
            String error = (e.getMessage() != null) ? e.getMessage() : "Unknown error";
            Toast.makeText(MainActivity.this, "Session error: " + error, Toast.LENGTH_LONG).show();
        });
    }

    @Override
    public void onPreviewStarted() {
        Log.d(TAG, "Preview started.");
    }

    @Override
    public void onSessionConfigured() {
        Log.d(TAG, "Session configured.");
        // Once the session is configured, we can start the stream in a background thread
        new Thread(() -> {
            if (mSession != null) {
                mSession.start();
            }
        }).start();
    }

    @Override
    public void onSessionStarted() {
        Log.d(TAG, "Session started.");
    }

    @Override
    public void onSessionStopped() {
        Log.d(TAG, "Session stopped.");
    }
}
