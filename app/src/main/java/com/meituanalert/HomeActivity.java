package com.meituanalert;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;

public class HomeActivity extends Activity {

    private Prefs prefs;
    private MediaPlayer preview;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        prefs = new Prefs(this);

        View btnSms = findViewById(R.id.btn_home_sms);
        View btnCheckin = findViewById(R.id.btn_home_checkin);

        btnSms.setOnClickListener(v -> startActivity(new Intent(this, SmsActivity.class)));
        btnCheckin.setOnClickListener(v -> startActivity(new Intent(this, CheckInActivity.class)));

        // 首次运行：弹出提醒音量设置
        if (!prefs.isVolumeConfigured()) {
            showFirstRunVolumeDialog();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        TextView tv = findViewById(R.id.tv_home_status);
        if (tv != null) {
            tv.setText("美团提醒：" + (prefs.isFixedEnabled() || prefs.isCountdownEnabled() ? "已开启" : "已关闭")
                    + "    打卡助手：" + (prefs.isCheckinEnabled() ? "已开启" : "已关闭"));
        }
    }

    private void showFirstRunVolumeDialog() {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_volume, null);
        SeekBar sb = v.findViewById(R.id.dialog_seek_volume);
        Button play = v.findViewById(R.id.dialog_btn_play);
        sb.setMax(100);
        sb.setProgress(70);

        play.setOnClickListener(x -> playPreview(sb.getProgress() / 100f));

        new AlertDialog.Builder(this)
                .setTitle("选择提醒音量")
                .setMessage("这是提醒音的音量，之后可在「美团提醒」页面随时修改。")
                .setView(v)
                .setPositiveButton("保存", (d, w) -> {
                    prefs.setAlarmVolume(sb.getProgress() / 100f);
                    stopPreview();
                })
                .setNegativeButton("跳过（默认最大）", (d, w) -> {
                    prefs.setAlarmVolume(1.0f);
                    stopPreview();
                })
                .setOnDismissListener(d -> stopPreview())
                .show();
    }

    private void playPreview(float vol) {
        stopPreview();
        try {
            preview = MediaPlayer.create(this, R.raw.alert);
            if (preview != null) {
                if (Build.VERSION.SDK_INT >= 21) {
                    preview.setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build());
                }
                preview.setVolume(vol, vol);
                preview.start();
            }
        } catch (Exception ignored) { }
    }

    private void stopPreview() {
        try {
            if (preview != null) {
                preview.stop();
                preview.release();
                preview = null;
            }
        } catch (Exception ignored) { }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopPreview();
    }
}
