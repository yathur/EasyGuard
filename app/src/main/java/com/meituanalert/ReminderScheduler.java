package com.meituanalert;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.util.Calendar;

/** 定时提醒调度：固定时间（每天）+ 倒计时（一次性） */
public class ReminderScheduler {
    private static final int RC_FIXED = 3001;
    private static final int RC_COUNTDOWN = 3002;

    /** 每天固定时间提醒；若今天该时间已过则排到明天 */
    public static void scheduleFixed(Context c, Prefs p) {
        if (!p.isFixedEnabled()) return;
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        String[] hm = p.getFixedTime().split(":");
        if (hm.length != 2) return;
        int hh, mm;
        try {
            hh = Integer.parseInt(hm[0].trim());
            mm = Integer.parseInt(hm[1].trim());
        } catch (Exception e) {
            return;
        }
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, hh);
        cal.set(Calendar.MINUTE, mm);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }
        PendingIntent pi = PendingIntent.getBroadcast(c, RC_FIXED,
                new Intent(c, ReminderReceiver.class)
                        .putExtra(ReminderReceiver.EXTRA_TYPE, ReminderReceiver.TYPE_FIXED),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        setAlarm(am, cal.getTimeInMillis(), pi);
        Log.i("ReminderScheduler", "fixed scheduled at " + cal.getTime().toString());
    }

    public static void cancelFixed(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = PendingIntent.getBroadcast(c, RC_FIXED,
                new Intent(c, ReminderReceiver.class)
                        .putExtra(ReminderReceiver.EXTRA_TYPE, ReminderReceiver.TYPE_FIXED),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.cancel(pi);
    }

    /** N 分钟后提醒一次 */
    public static void scheduleCountdown(Context c, int minutes) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        long at = System.currentTimeMillis() + minutes * 60_000L;
        PendingIntent pi = PendingIntent.getBroadcast(c, RC_COUNTDOWN,
                new Intent(c, ReminderReceiver.class)
                        .putExtra(ReminderReceiver.EXTRA_TYPE, ReminderReceiver.TYPE_COUNTDOWN)
                        .putExtra("minutes", minutes),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        setAlarm(am, at, pi);
        Prefs p = new Prefs(c);
        p.setCountdownAt(at);
        p.setCountdownMinutes(minutes);
        Log.i("ReminderScheduler", "countdown scheduled in " + minutes + "min");
    }

    public static void cancelCountdown(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = PendingIntent.getBroadcast(c, RC_COUNTDOWN,
                new Intent(c, ReminderReceiver.class)
                        .putExtra(ReminderReceiver.EXTRA_TYPE, ReminderReceiver.TYPE_COUNTDOWN),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.cancel(pi);
        new Prefs(c).setCountdownAt(0);
    }

    private static void setAlarm(AlarmManager am, long at, PendingIntent pi) {
        try {
            // 非精确闹钟：省电模式下可能延迟触发，但无需额外授权
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        } catch (Exception e) {
            am.set(AlarmManager.RTC_WAKEUP, at, pi);
        }
    }
}
