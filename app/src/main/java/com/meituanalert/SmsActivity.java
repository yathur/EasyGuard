package com.meituanalert;

import android.app.Activity;
import android.app.NotificationManager;
import android.app.TimePickerDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public class SmsActivity extends Activity {

    private Prefs prefs;
    private SeekBar seekVolume;
    private TextView tvVolume;
    private Switch swFixed;
    private EditText etFixedTime;
    private Switch swCountdown;
    private EditText etCountdown;
    private Button btnMinus;
    private Button btnPlus;
    private TextView tvCountdownState;
    private MediaPlayer preview;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sms);

        prefs = new Prefs(this);

        seekVolume = findViewById(R.id.seek_volume);
        tvVolume = findViewById(R.id.tv_volume);
        swFixed = findViewById(R.id.sw_fixed);
        etFixedTime = findViewById(R.id.et_fixed_time);
        swCountdown = findViewById(R.id.sw_countdown);
        etCountdown = findViewById(R.id.et_countdown);
        btnMinus = findViewById(R.id.btn_minus);
        btnPlus = findViewById(R.id.btn_plus);
        tvCountdownState = findViewById(R.id.tv_countdown_state);

        seekVolume.setMax(100);
        seekVolume.setProgress(Math.round(prefs.getAlarmVolume() * 100));
        tvVolume.setText("提醒音量：" + seekVolume.getProgress() + "%");
        swFixed.setChecked(prefs.isFixedEnabled());
        etFixedTime.setText(prefs.getFixedTime());
        etFixedTime.setFocusable(false);
        etFixedTime.setOnClickListener(v -> showTimePicker());
        swCountdown.setChecked(prefs.isCountdownEnabled());
        etCountdown.setText(String.valueOf(prefs.getCountdownMinutes()));
        updateEditableStates();
        refreshCountdownState();

        swFixed.setOnCheckedChangeListener((b, isChecked) -> {
            prefs.setFixedEnabled(isChecked);
            if (isChecked) {
                String t = etFixedTime.getText().toString().trim();
                if (t.matches("\\d{2}:\\d{2}")) prefs.setFixedTime(t);
                ReminderScheduler.scheduleFixed(this, prefs);
            } else {
                ReminderScheduler.cancelFixed(this);
            }
            updateEditableStates();
        });

        swCountdown.setOnCheckedChangeListener((b, isChecked) -> {
            prefs.setCountdownEnabled(isChecked);
            if (isChecked) {
                int min = getCountdownValue();
                prefs.setCountdownMinutes(min);
                etCountdown.setText(String.valueOf(min));
                ReminderScheduler.scheduleCountdown(this, min);
            } else {
                ReminderScheduler.cancelCountdown(this);
            }
            updateEditableStates();
            refreshCountdownState();
        });

        btnMinus.setOnClickListener(v -> adjustCountdown(-1));
        btnPlus.setOnClickListener(v -> adjustCountdown(1));

        seekVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                tvVolume.setText("提醒音量：" + p + "%");
            }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        });

        Button btnPlay = findViewById(R.id.btn_play_sms);
        btnPlay.setOnClickListener(v -> playPreview(seekVolume.getProgress() / 100f));

        // Android 14+ 全屏提醒受系统限制：未授权时引导去设置开启"闹钟和提醒"
        Button btnFull = findViewById(R.id.btn_full_screen);
        if (Build.VERSION.SDK_INT >= 34) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null && nm.canUseFullScreenIntent()) {
                btnFull.setVisibility(View.GONE);
            } else {
                btnFull.setOnClickListener(v -> {
                    openAppSettings();
                    Toast.makeText(this, "请在应用信息中找到“闹钟和提醒”并设为允许，锁屏才能全屏弹提醒",
                            Toast.LENGTH_LONG).show();
                });
            }
        } else {
            btnFull.setVisibility(View.GONE);
        }

        Button btnTest = findViewById(R.id.btn_test_sms);
        btnTest.setOnClickListener(v -> {
            prefs.setAlarmVolume(seekVolume.getProgress() / 100f);
            Notifier.fire(this, "模拟提醒", "", "这是一条测试，检查闹铃是否正常");
        });

        Button btnSave = findViewById(R.id.btn_save_sms);
        btnSave.setOnClickListener(v -> {
            prefs.setAlarmVolume(seekVolume.getProgress() / 100f);
            String t = etFixedTime.getText().toString().trim();
            if (t.matches("\\d{2}:\\d{2}")) prefs.setFixedTime(t);
            if (prefs.isFixedEnabled()) {
                ReminderScheduler.scheduleFixed(this, prefs);
            }
            if (prefs.isCountdownEnabled()) {
                int min = getCountdownValue();
                prefs.setCountdownMinutes(min);
                etCountdown.setText(String.valueOf(min));
                ReminderScheduler.scheduleCountdown(this, min);
            }
            etFixedTime.setText(prefs.getFixedTime());
            updateEditableStates();
            refreshCountdownState();
            Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show();
        });
    }

    /** 弹出系统时间选择器，选好即保存；若固定提醒开着则立即重新排程 */
    private void showTimePicker() {
        String t = etFixedTime.getText().toString().trim();
        int hh = 9, mm = 0;
        if (t.matches("\\d{2}:\\d{2}")) {
            String[] hm = t.split(":");
            try {
                hh = Integer.parseInt(hm[0].trim());
                mm = Integer.parseInt(hm[1].trim());
            } catch (Exception ignored) { }
        }
        TimePickerDialog dlg = new TimePickerDialog(this, (view, hourOfDay, minute) -> {
            String val = String.format(java.util.Locale.US, "%02d:%02d", hourOfDay, minute);
            etFixedTime.setText(val);
            prefs.setFixedTime(val);
            if (prefs.isFixedEnabled()) {
                ReminderScheduler.scheduleFixed(this, prefs);
            }
        }, hh, mm, true);
        // 强制按钮为中文（系统语言为英文时 TimePicker 默认显示 OK/Cancel）
        dlg.setButton(DialogInterface.BUTTON_POSITIVE, "确定", dlg);
        dlg.setButton(DialogInterface.BUTTON_NEGATIVE, "取消", dlg);
        dlg.show();
    }

    private int getCountdownValue() {
        try {
            int v = Integer.parseInt(etCountdown.getText().toString().trim());
            return Math.max(1, Math.min(720, v));
        } catch (Exception e) {
            return 60;
        }
    }

    private void adjustCountdown(int delta) {
        int v = getCountdownValue() + delta;
        v = Math.max(1, Math.min(720, v));
        prefs.setCountdownMinutes(v);
        etCountdown.setText(String.valueOf(v));
        if (prefs.isCountdownEnabled()) {
            ReminderScheduler.scheduleCountdown(this, v);
            refreshCountdownState();
            Toast.makeText(this, "倒计时已更新为 " + v + " 分钟后提醒", Toast.LENGTH_SHORT).show();
        }
    }

    /** 开关打开后锁定对应参数，防止误改 */
    private void updateEditableStates() {
        boolean fixedOn = prefs.isFixedEnabled();
        etFixedTime.setEnabled(!fixedOn);
        etFixedTime.setFocusable(false);
        etFixedTime.setFocusableInTouchMode(false);
        etFixedTime.setTextColor(fixedOn ? 0xFFBDBDBD : 0xFF000000);

        boolean cdOn = prefs.isCountdownEnabled();
        etCountdown.setEnabled(!cdOn);
        etCountdown.setFocusable(!cdOn);
        etCountdown.setFocusableInTouchMode(!cdOn);
        etCountdown.setTextColor(cdOn ? 0xFFBDBDBD : 0xFF000000);
        btnMinus.setEnabled(!cdOn);
        btnPlus.setEnabled(!cdOn);
    }

    private void refreshCountdownState() {
        long at = prefs.getCountdownAt();
        if (prefs.isCountdownEnabled() && at > System.currentTimeMillis()) {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.US);
            tvCountdownState.setText("倒计时：已开启，" + prefs.getCountdownMinutes()
                    + " 分钟后（约 " + sdf.format(new java.util.Date(at)) + "）提醒");
        } else if (prefs.isCountdownEnabled()) {
            tvCountdownState.setText("倒计时：已开启");
        } else {
            tvCountdownState.setText("倒计时：未开启");
        }
    }

    private void openAppSettings() {
        try {
            Intent it = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName()));
            startActivity(it);
        } catch (Exception e) {
            Toast.makeText(this, "请到系统设置 → 应用 → 本应用 中开启", Toast.LENGTH_LONG).show();
        }
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
