package com.example.frigatecamerappv3;

import net.majorkernelpanic.streaming.SessionBuilder;
import net.majorkernelpanic.streaming.gl.SurfaceView;
import net.majorkernelpanic.streaming.rtsp.RtspServer;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences.Editor;
import android.content.pm.ActivityInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.format.Formatter;
import android.view.WindowManager;
import android.widget.TextView;

public class MainActivity extends Activity {

	private SurfaceView mSurfaceView;
	private TextView mUrlTextView;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		setContentView(R.layout.activity_main);
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

		mSurfaceView = (SurfaceView) findViewById(R.id.surface);
		mUrlTextView = (TextView) findViewById(R.id.tv_url);

		// Sets the port of the RTSP server to 1234
		Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
		editor.putString(RtspServer.KEY_PORT, String.valueOf(1234));
		editor.commit();

		// Configures the SessionBuilder
		SessionBuilder.getInstance()
				.setSurfaceView(mSurfaceView)
				.setPreviewOrientation(90)
				.setContext(getApplicationContext())
				.setAudioEncoder(SessionBuilder.AUDIO_NONE)
				.setVideoEncoder(SessionBuilder.VIDEO_H264);

		// Starts the RTSP server
		this.startService(new Intent(this, RtspServer.class));

		// Display the RTSP URL
		mUrlTextView.setText(getIpAddress() + ":1234");
	}

	private String getIpAddress() {
		try {
			WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
			return "rtsp://" + Formatter.formatIpAddress(wifiManager.getConnectionInfo().getIpAddress());
		} catch (Exception e) {
			return "IP not available";
		}
	}
}
