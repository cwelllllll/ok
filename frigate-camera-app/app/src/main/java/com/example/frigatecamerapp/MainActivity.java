package com.example.frigatecamerapp;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.text.format.Formatter;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.pedro.rtspserver.RtspServerCamera2;
import com.pedro.rtspserver.util.ConnectCheckerRtsp;

/**
 * Main activity for the Frigate Camera App.
 * This activity handles camera permissions, displays the camera preview,
 * and manages the RTSP server for streaming video.
 */
public class MainActivity extends AppCompatActivity implements ConnectCheckerRtsp {

    private static final String TAG = "MainActivity";
    private final String[] REQUIRED_PERMISSIONS = new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO};
    private static final int RTSP_PORT = 1935; // Standard RTSP port, can be changed.

    private RtspServerCamera2 rtspServerCamera2;
    private Button startStopButton;
    private TextView infoText;
    private PreviewView previewView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Keep the screen on while the app is active
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        previewView = findViewById(R.id.previewView);
        startStopButton = findViewById(R.id.start_stop_button);
        infoText = findViewById(R.id.info_text);

        // Check for camera and audio permissions. If granted, set up the server.
        // Otherwise, request permissions.
        if (allPermissionsGranted()) {
            setupRtspServer();
        } else {
            requestPermissionsLauncher.launch(REQUIRED_PERMISSIONS);
        }
    }

    /**
     * Initializes the RTSP server and sets up the start/stop button.
     */
    private void setupRtspServer() {
        rtspServerCamera2 = new RtspServerCamera2(previewView, this, RTSP_PORT);
        startStopButton.setOnClickListener(v -> {
            if (!rtspServerCamera2.isStreaming()) {
                // Prepare video and audio encoders
                if (rtspServerCamera2.prepareAudio() && rtspServerCamera2.prepareVideo()) {
                    // Start the stream
                    startStopButton.setText("Stop Stream");
                    rtspServerCamera2.startStream();
                    updateUrl(); // Update the URL text view
                } else {
                    Toast.makeText(this, "Error preparing stream. Check logs for details.", Toast.LENGTH_SHORT).show();
                }
            } else {
                // Stop the stream
                startStopButton.setText("Start Stream");
                rtspServerCamera2.stopStream();
                infoText.setText("Stream stopped");
            }
        });
        updateUrl(); // Set initial URL text
    }

    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    /**
     * Modern way to handle permission requests.
     */
    private final ActivityResultLauncher<String[]> requestPermissionsLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                if (Boolean.TRUE.equals(result.get(Manifest.permission.CAMERA)) && Boolean.TRUE.equals(result.get(Manifest.permission.RECORD_AUDIO))) {
                    // Permissions granted, set up the server
                    setupRtspServer();
                } else {
                    // Permissions denied, show a message and close the app
                    Toast.makeText(this, "Permissions not granted. App will close.", Toast.LENGTH_LONG).show();
                    finish();
                }
            });

    /**
     * Gets the local IP address of the device.
     * @return The formatted IP address or "0.0.0.0" if not available.
     */
    private String getIpAddress() {
        try {
            WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            return Formatter.formatIpAddress(wifiManager.getConnectionInfo().getIpAddress());
        } catch (Exception e) {
            Log.e(TAG, "Failed to get IP address", e);
            return "0.0.0.0";
        }
    }

    /**
     * Updates the TextView to display the current RTSP stream URL.
     */
    private void updateUrl() {
        String ip = getIpAddress();
        String url = "rtsp://" + ip + ":" + RTSP_PORT + "/";
        infoText.setText(url);
    }

    // ConnectCheckerRtsp callbacks
    // These are called from the RTSP server library thread, so we use runOnUiThread to update the UI.

    @Override
    public void onConnectionSuccessRtsp() {
        runOnUiThread(() -> Toast.makeText(MainActivity.this, "Stream started successfully", Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onConnectionFailedRtsp(String reason) {
        runOnUiThread(() -> {
            Toast.makeText(MainActivity.this, "Connection failed: " + reason, Toast.LENGTH_SHORT).show();
            rtspServerCamera2.stopStream();
            startStopButton.setText("Start Stream");
            infoText.setText("Stream stopped");
        });
    }

    @Override
    public void onNewBitrateRtsp(long bitrate) {
        // This callback can be used to monitor the stream bitrate in real-time.
        // Not used in this basic implementation.
    }

    @Override
    public void onDisconnectRtsp() {
        runOnUiThread(() -> {
            Toast.makeText(MainActivity.this, "Stream disconnected", Toast.LENGTH_SHORT).show();
            startStopButton.setText("Start Stream");
            infoText.setText("Stream stopped");
        });
    }

    @Override
    public void onAuthErrorRtsp() {
        runOnUiThread(() -> Toast.makeText(MainActivity.this, "Auth error", Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onAuthSuccessRtsp() {
        runOnUiThread(() -> Toast.makeText(MainActivity.this, "Auth success", Toast.LENGTH_SHORT).show());
    }
}
