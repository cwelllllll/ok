package com.example.rtspserver;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.pedro.encoder.input.video.CameraOpenException;
import com.pedro.library.rtsp.RtspCamera2;
import com.pedro.rtspserver.util.ConnectCheckerRtsp;
import com.pedro.encoder.input.video.Camera2View;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

public class MainActivity extends AppCompatActivity implements ConnectCheckerRtsp {

    private static final String TAG = "MainActivity";
    private static final int PERMISSIONS_REQUEST_CODE = 101;

    private RtspCamera2 rtspCamera2;
    private Button startStopButton;
    private TextView rtspUrlTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        Camera2View camera2View = findViewById(R.id.surfaceView);
        startStopButton = findViewById(R.id.start_stop_button);
        rtspUrlTextView = findViewById(R.id.rtsp_url);

        // We are using the RtspCamera2 class from the library, which handles the camera
        // and the RTSP client logic. We pass the view and the connection checker.
        rtspCamera2 = new RtspCamera2(camera2View, this);
        rtspCamera2.setReTries(10); // Optional: Re-try connection if it fails

        startStopButton.setOnClickListener(v -> {
            if (!checkPermissions()) {
                requestPermissions();
                return;
            }
            toggleStream();
        });
    }

    private void toggleStream() {
        if (!rtspCamera2.isStreaming()) {
            try {
                if (rtspCamera2.prepareAudio() && rtspCamera2.prepareVideo()) {
                    rtspCamera2.startStream(getRtspUrl());
                } else {
                    Toast.makeText(this, "Error preparing stream, check permissions and camera.", Toast.LENGTH_SHORT).show();
                }
            } catch (CameraOpenException e) {
                Toast.makeText(this, "Error opening camera: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        } else {
            rtspCamera2.stopStream();
        }
        updateUI();
    }

    private void updateUI() {
        runOnUiThread(() -> {
            if (rtspCamera2.isStreaming()) {
                startStopButton.setText("Stop Stream");
                rtspUrlTextView.setText(getRtspUrl());
            } else {
                startStopButton.setText("Start Stream");
                rtspUrlTextView.setText("");
            }
        });
    }

    private String getRtspUrl() {
        // The server is now part of the library, so we don't need a separate service.
        // The RtspCamera2 class handles the server internally.
        // The default port in the library is 1935.
        return "rtsp://" + getLocalIpAddress() + ":1935";
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
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.INTERNET) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO, Manifest.permission.INTERNET}, PERMISSIONS_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            if (!checkPermissions()) {
                Toast.makeText(this, "Permissions are required to run the app.", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    // ConnectCheckerRtsp callbacks
    @Override
    public void onAuthErrorRtsp() {
        Log.e(TAG, "RTSP Auth Error");
        runOnUiThread(() -> {
            rtspCamera2.stopStream();
            Toast.makeText(this, "RTSP Auth Error", Toast.LENGTH_SHORT).show();
            updateUI();
        });
    }

    @Override
    public void onAuthSuccessRtsp() {
        Log.d(TAG, "RTSP Auth Success");
    }

    @Override
    public void onConnectionFailedRtsp(String reason) {
        Log.e(TAG, "RTSP Connection Failed: " + reason);
        runOnUiThread(() -> {
            rtspCamera2.stopStream();
            Toast.makeText(this, "RTSP Connection Failed: " + reason, Toast.LENGTH_SHORT).show();
            updateUI();
        });
    }

    @Override
    public void onConnectionSuccessRtsp() {
        Log.d(TAG, "RTSP Connection Success");
        runOnUiThread(this::updateUI);
    }

    @Override
    public void onNewBitrateRtsp(long bitrate) {
    }

    @Override
    public void onDisconnectRtsp() {
        Log.d(TAG, "RTSP Client Disconnected");
        runOnUiThread(this::updateUI);
    }
}
