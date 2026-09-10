package com.meituanalert;

import android.app.Activity;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

public class AlertActivity extends Activity {
    private MediaPlayer player;
    private Vibrator vibrator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                        | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                        | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);

        setContentView(R.layout.activity_alert);

        String title = getIntent().getStringExtra("title");
        String sender = getIntent().getStringExtra("sender");
        String body = getIntent().getStringExtra("body");
        TextView tvSub = findViewById(R.id.alert_subtitle);
        if (title != null && !title.isEmpty()) {
            tvSub.setText(title);
        }
        TextView tvSender = findViewById(R.id.alert_sender);
        TextView tvBody = findViewById(R.id.alert_body);
        if (sender != null && !sender.isEmpty()) {
            tvSender.setText("发件人：" + sender);
        } else {
            tvSender.setVisibility(View.GONE);
        }
        tvBody.setText(body == null || body.isEmpty() ? "收到美团相关短信，请检查是否忘记锁车" : body);

        Button ok = findViewById(R.id.alert_ok);
        ok.setOnClickListener(v -> dismiss());

        // 全屏页已弹出：停掉 Notifier 的兜底闹铃，改由本页继续循环播放，避免双声
        Notifier.stopAlarmSound();
        startAlarm();
    }

    private void startAlarm() {
        try {
            player = MediaPlayer.create(this, R.raw.alert);
            if (player != null) {
                if (Build.VERSION.SDK_INT >= 21) {
                    player.setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build());
                }
                float vol = new Prefs(this).getAlarmVolume();
                player.setVolume(vol, vol);
                player.setLooping(true);
                player.start();
            }
        } catch (Exception ignored) { }

        try {
            vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                long[] pattern = {0, 600, 300, 600, 300, 600};
                if (Build.VERSION.SDK_INT >= 26) {
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
                } else {
                    vibrator.vibrate(pattern, 0);
                }
            }
        } catch (Exception ignored) { }
    }

    private void dismiss() {
        try {
            if (player != null) {
                player.stop();
                player.release();
                player = null;
            }
        } catch (Exception ignored) { }
        try {
            if (vibrator != null) vibrator.cancel();
        } catch (Exception ignored) { }
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (player != null) {
                player.stop();
                player.release();
                player = null;
            }
        } catch (Exception ignored) { }
        try {
            if (vibrator != null) vibrator.cancel();
        } catch (Exception ignored) { }
    }
}
