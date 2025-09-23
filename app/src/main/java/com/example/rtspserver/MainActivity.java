package com.example.rtspserver;

import android.Manifest;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import net.majorkernelpanic.streaming.Session;
import net.majorkernelpanic.streaming.gl.SurfaceView;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

public class MainActivity extends AppCompatActivity implements Session.Callback, SurfaceHolder.Callback {

    private final static int PERMISSION_REQUEST_CODE = 1;
    private SurfaceView mSurfaceView;
    private Button mButton;
    private TextView mUrlTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        mSurfaceView = findViewById(R.id.surface);
        mButton = findViewById(R.id.start_stop_button);
        mUrlTextView = findViewById(R.id.rtsp_url);

        // Start the server service once
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(new Intent(this, RtspServer.class));
        } else {
            startService(new Intent(this, RtspServer.class));
        }

        if (!hasPermissions()) {
            requestPermissions();
        } else {
            initializeApp();
        }
    }

    private boolean hasPermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO},
                PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                initializeApp();
            } else {
                Toast.makeText(this, "Permissions not granted. App cannot function.", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    private void initializeApp() {
        mSurfaceView.getHolder().addCallback(this);
        mButton.setOnClickListener(v -> {
            RtspServer server = RtspServerSingleton.getServer();
            if (server != null) {
                Session session = server.getSession();
                if (session != null) {
                    if (session.isStreaming()) {
                        new Thread(() -> session.stop()).start();
                    } else {
                        new Thread(() -> session.start()).start();
                    }
                }
            }
        });
    }

    private String getIpAddress() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
                if (intf.getName().toLowerCase().contains("wlan")) {
                    for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                        InetAddress inetAddress = enumIpAddr.nextElement();
                        if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address) {
                            return inetAddress.getHostAddress();
                        }
                    }
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address) {
                        return inetAddress.getHostAddress();
                    }
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }
        return "127.0.0.1";
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopService(new Intent(this, RtspServer.class));
    }

    // Session.Callback methods
    @Override
    public void onBitrateUpdate(long bitrate) {}

    @Override
    public void onSessionError(int reason, int streamType, Exception e) {
        runOnUiThread(() -> {
            String error = "Session error: " + (e != null ? e.getMessage() : "Unknown error");
            Toast.makeText(MainActivity.this, error, Toast.LENGTH_LONG).show();
            mButton.setText("Start");
        });
    }

    @Override
    public void onPreviewStarted() {}

    @Override
    public void onSessionConfigured() {}

    @Override
    public void onSessionStarted() {
        runOnUiThread(() -> {
            mButton.setText("Stop");
            mUrlTextView.setText("rtsp://" + getIpAddress() + ":8086");
        });
    }

    @Override
    public void onSessionStopped() {
        runOnUiThread(() -> {
            mButton.setText("Start");
            mUrlTextView.setText("RTSP URL: ");
        });
    }

    // SurfaceHolder.Callback methods
    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        RtspServer server = RtspServerSingleton.getServer();
        if (server != null) {
            Session session = server.getSession();
            if (session != null) {
                session.setSurfaceView(mSurfaceView);
                session.setCallback(this);
                session.startPreview();
            }
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        RtspServer server = RtspServerSingleton.getServer();
        if (server != null) {
            Session session = server.getSession();
            if (session != null) {
                session.stopPreview();
            }
        }
    }
}
