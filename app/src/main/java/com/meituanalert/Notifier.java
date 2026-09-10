package com.meituanalert;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;

public class Notifier {
    private static final String CHANNEL_ID = "sms_alert_channel";
    public static final int NOTIF_ID = 2001;

    private static MediaPlayer alarmPlayer;
    private static Vibrator alarmVibrator;

    public static void ensureChannel(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "美团提醒", NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("收到美团相关短信时以最高优先级提醒");
        Uri sound = Uri.parse("android.resource://" + c.getPackageName() + "/" + R.raw.alert);
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        ch.setSound(sound, attrs);
        ch.enableVibration(true);
        ch.setVibrationPattern(new long[]{0, 500, 200, 500, 200, 500});
        nm.createNotificationChannel(ch);
    }

    /** 触发提醒：全屏通知 + 尝试直接拉起 AlertActivity（默认标题） */
    public static void fire(Context c, String sender, String body) {
        fire(c, "美团提醒：可能忘记锁车！", sender, body);
    }

    /** 触发提醒：全屏通知 + 尝试直接拉起 AlertActivity（自定义标题） */
    public static void fire(Context c, String title, String sender, String body) {
        ensureChannel(c);
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        Intent full = new Intent(c, AlertActivity.class);
        full.putExtra("title", title == null ? "" : title);
        full.putExtra("sender", sender == null ? "" : sender);
        full.putExtra("body", body == null ? "" : body);
        full.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 0, full,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification n = new Notification.Builder(c, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(title == null ? "美团提醒：可能忘记锁车！" : title)
                .setContentText(body == null ? "收到美团相关短信" : body)
                .setPriority(Notification.PRIORITY_MAX)
                .setCategory(Notification.CATEGORY_ALARM)
                .setContentIntent(pi)
                .setFullScreenIntent(pi, true)
                .setAutoCancel(true)
                .build();
        try {
            nm.notify(NOTIF_ID, n);
        } catch (Exception e) {
            Log.w("Notifier", "notify failed", e);
        }

        // 兜底：直接尝试启动全屏页（部分系统可能拦截后台启动，失败不影响通知通道）
        try {
            c.startActivity(full);
        } catch (Exception e) {
            Log.w("Notifier", "direct start failed, notification should cover", e);
        }

        // 兜底闹铃：不依赖通知与 Activity，直接循环播放提醒音 + 震动（锁屏/骑车也能听到）
        startAlarmSound(c);
    }

    /**
     * 直接播放循环提醒音 + 循环震动。
     * 音量取用户设置；即便全屏页面被系统拦截、通知被静音，闹铃依然会响。
     */
    public static void startAlarmSound(Context c) {
        stopAlarmSound();
        try {
            float vol = new Prefs(c).getAlarmVolume();
            MediaPlayer mp = MediaPlayer.create(c, R.raw.alert);
            if (mp != null) {
                if (Build.VERSION.SDK_INT >= 21) {
                    mp.setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build());
                }
                mp.setVolume(vol, vol);
                mp.setLooping(true);
                mp.start();
                alarmPlayer = mp;
            }
        } catch (Exception e) {
            Log.w("Notifier", "alarm sound failed", e);
        }
        try {
            Vibrator v = (Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null && v.hasVibrator()) {
                long[] pattern = {0, 600, 300, 600, 300, 600};
                if (Build.VERSION.SDK_INT >= 26) {
                    v.vibrate(VibrationEffect.createWaveform(pattern, 0));
                } else {
                    v.vibrate(pattern, 0);
                }
                alarmVibrator = v;
            }
        } catch (Exception ignored) { }
    }

    /** 停止兜底闹铃（全屏页打开或用户处理后调用） */
    public static void stopAlarmSound() {
        try {
            if (alarmPlayer != null) {
                alarmPlayer.stop();
                alarmPlayer.release();
                alarmPlayer = null;
            }
        } catch (Exception ignored) { }
        try {
            if (alarmVibrator != null) alarmVibrator.cancel();
        } catch (Exception ignored) { }
    }
}
