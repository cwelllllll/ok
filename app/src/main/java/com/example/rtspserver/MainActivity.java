package com.example.rtspserver;

import android.Manifest;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import net.majorkernelpanic.streaming.Session;
import net.majorkernelpanic.streaming.SessionBuilder;
import net.majorkernelpanic.streaming.audio.AudioQuality;
import net.majorkernelpanic.streaming.gl.SurfaceView;
import net.majorkernelpanic.streaming.rtsp.RtspServer;
import net.majorkernelpanic.streaming.video.VideoQuality;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity implements Session.Callback, SurfaceHolder.Callback {

    private final static int PERMISSION_REQUEST_CODE = 1;
    private Session mSession;
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

        // Configures the RTSP server and the session
        mSession = SessionBuilder.getInstance()
                .setCallback(this)
                .setSurfaceView(mSurfaceView)
                .setPreviewOrientation(90)
                .setContext(getApplicationContext())
                .setAudioEncoder(SessionBuilder.AUDIO_AAC)
                .setAudioQuality(new AudioQuality(16000, 32000))
                .setVideoEncoder(SessionBuilder.VIDEO_H264)
                .setVideoQuality(new VideoQuality(640, 480, 20, 500000))
                .build();

        // Starts the RTSP server
        this.startService(new Intent(this, RtspServer.class));

        mButton.setOnClickListener(v -> {
            if (mSession.isStreaming()) {
                mSession.stop();
                mButton.setText("Start Server");
            } else {
                mSession.start();
                mButton.setText("Stop Server");
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mSession != null && mSession.isStreaming()) {
            mButton.setText("Stop Server");
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mSession != null && mSession.isStreaming()) {
            mSession.stop();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mSession != null) {
            mSession.release();
        }
        this.stopService(new Intent(this, RtspServer.class));
    }

    @Override
    public void onBitrateUpdate(long bitrate) {
        // Not used
    }

    @Override
    public void onSessionError(int reason, int streamType, Exception e) {
        // Not used
    }

    @Override
    public void onPreviewStarted() {
        // Not used
    }

    @Override
    public void onSessionConfigured() {
        // Not used
    }



    @Override
    public void onSessionStarted() {
        // We get the address of the stream here
        runOnUiThread(() -> mUrlTextView.setText(mSession.getSessionDescription().toString().split(" ")[2].replace("m=video", "trackID=1")));
    }

    @Override
    public void onSessionStopped() {
        runOnUiThread(() -> mUrlTextView.setText("RTSP URL: "));
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        // Starts the preview of the camera
        if (mSession != null) {
             mSession.startPreview();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        // Not used
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        // Stops the streaming session
         if (mSession != null && mSession.isStreaming()) {
            mSession.stop();
        }
    }
}
