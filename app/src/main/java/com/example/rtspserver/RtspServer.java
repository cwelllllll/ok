package com.example.rtspserver;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;

import net.majorkernelpanic.streaming.Session;
import net.majorkernelpanic.streaming.SessionBuilder;
import net.majorkernelpanic.streaming.audio.AudioQuality;
import net.majorkernelpanic.streaming.video.VideoQuality;

public class RtspServer extends Service {

    public final static String TAG = "RtspServer";
    private final static int NOTIFICATION_ID = 12345;
    private final static String CHANNEL_ID = "RTSPSERVER_CHANNEL";

    private Session mSession;
    private net.majorkernelpanic.streaming.rtsp.RtspServer mServer;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        // We need a notification for foreground services
        createNotificationChannel();
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("RTSP Server")
                .setContentText("Streaming service is running.")
                .setSmallIcon(R.mipmap.ic_launcher) // Note: This will need a real icon
                .build();

        startForeground(NOTIFICATION_ID, notification);

        // We configure the SessionBuilder here, but we don't need a surface view
        // as this service runs in the background. The activity will set its surface later.
        mSession = SessionBuilder.getInstance()
                .setContext(getApplicationContext())
                .setAudioEncoder(SessionBuilder.AUDIO_AAC)
                .setAudioQuality(new AudioQuality(16000, 32000))
                .setVideoEncoder(SessionBuilder.VIDEO_H264)
                .setVideoQuality(new VideoQuality(320, 240, 20, 500000))
                .build();

        // We start the RTSP server
        mServer = new net.majorkernelpanic.streaming.rtsp.RtspServer();
        mServer.setPort(8086);
        mServer.addSession(mSession);

        new Thread(() -> {
            try {
                mServer.start();
            } catch (Exception e) {
                // If the server fails to start, we should probably stop the service.
                e.printStackTrace();
                stopSelf();
            }
        }).start();

        // We make the service available to the activity
        RtspServerSingleton.setServer(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // We want the service to continue running until it is explicitly stopped.
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mServer != null) {
            new Thread(() -> {
                if (mServer.isStreaming()) mServer.stop();
                if (mSession != null) mSession.release();
            }).start();
        }
        RtspServerSingleton.setServer(null);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "RTSP Server Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    // Public methods for the Activity to control the session
    public Session getSession() {
        return mSession;
    }
}

// A simple singleton to grant the activity access to the service instance
class RtspServerSingleton {
    private static RtspServer serverInstance = null;

    public static void setServer(RtspServer server) {
        serverInstance = server;
    }

    public static RtspServer getServer() {
        return serverInstance;
    }
}
