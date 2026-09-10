package com.meituanalert;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 打卡助手前台服务。
 * 时段感知调度：不在打卡时段内完全停止定位，只每 5 分钟检查一次时间；
 * 进入打卡时段才开启定位（30 秒一次），时段结束或次数已满即恢复休眠，省电。
 */
public class CheckInService extends Service {
    private static final String CHANNEL_ID = "checkin_channel";
    private static final int NOTIF_ID = 1001;
    private static final String TAG = "CheckInService";
    /** 模拟触发 action：手动测试后台打开钉钉是否被系统拦截 */
    public static final String ACTION_SIMULATE = "com.meituanalert.action.SIMULATE";

    /** 非时段时的检查间隔 */
    private static final long SCAN_MS = 5 * 60 * 1000L;
    /** 时段内的定位间隔 */
    private static final long LOC_INTERVAL_MS = 30 * 1000L;
    /** 时段结束兜底检查间隔 */
    private static final long SLOT_CHECK_MS = 60 * 1000L;

    private LocationManager lm;
    private Location last;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean locating;
    private int currentSlot;

    private final Runnable timeCheck = new Runnable() {
        @Override public void run() { scheduleNext(); }
    };

    private final Runnable slotEndCheck = new Runnable() {
        @Override public void run() {
            Prefs p = new Prefs(CheckInService.this);
            if (matchSlot(p) != currentSlot) {
                stopUpdates();
                scheduleNext();
            } else {
                handler.postDelayed(slotEndCheck, SLOT_CHECK_MS);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIF_ID, buildServiceNotification());
        // 模拟触发：走真实的后台打开钉钉路径，用于测试系统是否拦截后台弹出
        if (intent != null && ACTION_SIMULATE.equals(intent.getAction())) {
            openDingTalk(new Prefs(this));
            return START_STICKY;
        }
        handler.removeCallbacks(timeCheck);
        scheduleNext();
        return START_STICKY;
    }

    /** 主调度：只在打卡时段内开启定位，时段外每 5 分钟看一眼时间 */
    private void scheduleNext() {
        Prefs p = new Prefs(this);
        resetIfNewDay(p);
        if (!p.isCheckinEnabled() || !p.hasPoint()) {
            handler.postDelayed(timeCheck, SCAN_MS);
            return;
        }
        int slot = matchSlot(p);
        if (slot == 0) {
            stopUpdates();
            handler.postDelayed(timeCheck, SCAN_MS);
            return;
        }
        if (slotQuotaFull(p, slot)) {
            stopUpdates();
            handler.postDelayed(timeCheck, SCAN_MS);
            return;
        }
        startUpdates(slot);
    }

    private void startUpdates(int slot) {
        if (locating && currentSlot == slot) return;
        try {
            if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "no location permission");
                return;
            }
            stopUpdates();
            if (lm != null) {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, LOC_INTERVAL_MS, 3, listener);
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, LOC_INTERVAL_MS, 3, listener);
            }
            locating = true;
            currentSlot = slot;
            handler.postDelayed(slotEndCheck, SLOT_CHECK_MS);
        } catch (Exception e) {
            Log.e(TAG, "startUpdates failed", e);
        }
    }

    private void stopUpdates() {
        locating = false;
        currentSlot = 0;
        handler.removeCallbacks(slotEndCheck);
        try {
            if (lm != null) lm.removeUpdates(listener);
        } catch (Exception ignored) { }
    }

    private boolean slotQuotaFull(Prefs p, int slot) {
        if (slot == 1) return p.getMorningCountToday() >= p.getMorningCount();
        if (slot == 2) return p.getEveningCountToday() >= p.getEveningCount();
        return false;
    }

    /** 跨天时清零当日打卡计数 */
    private void resetIfNewDay(Prefs p) {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        if (!today.equals(p.getTriggeredDate())) {
            p.setTriggeredDate(today);
            p.setMorningCountToday(0);
            p.setEveningCountToday(0);
        }
    }

    private final LocationListener listener = new LocationListener() {
        @Override
        public void onLocationChanged(Location loc) {
            // 先记录本次位置，供下一次回调推算移动速度（网络定位自带速度恒为0，必须靠推算）
            last = loc;

            Prefs p = new Prefs(CheckInService.this);
            if (!p.isCheckinEnabled() || !p.hasPoint()) return;
            int slot = matchSlot(p);
            if (slot == 0) {
                stopUpdates();
                scheduleNext();
                return;
            }
            if (slotQuotaFull(p, slot)) {
                stopUpdates();
                scheduleNext();
                return;
            }

            float dist = distanceMeters(p.getLat(), p.getLng(), loc.getLatitude(), loc.getLongitude());
            float speed = loc.getSpeed(); // m/s
            if (speed <= 0 && last != null) {
                long dt = loc.getTime() - last.getTime();
                if (dt > 0) {
                    speed = (float) (loc.distanceTo(last) * 1000.0 / dt);
                }
            }
            Log.i(TAG, "loc: dist=" + dist + "/" + p.getRadius() + " speed=" + speed
                    + "/" + p.getSpeedThreshold() + " slot=" + slot
                    + " inRadius=" + (dist <= p.getRadius()) + " overSpeed=" + (speed >= p.getSpeedThreshold()));
            if (dist > p.getRadius()) return;
            if (speed < p.getSpeedThreshold()) return;

            Log.i(TAG, "trigger open dingtalk, slot=" + slot + " dist=" + dist + " speed=" + speed);
            openDingTalk(p);

            if (slot == 1) {
                p.setMorningCountToday(p.getMorningCountToday() + 1);
            } else {
                p.setEveningCountToday(p.getEveningCountToday() + 1);
            }
            if (slotQuotaFull(p, slot)) {
                stopUpdates();
                scheduleNext();
            }
        }

        @Override public void onStatusChanged(String provider, int status, Bundle extras) { }
        @Override public void onProviderEnabled(String provider) { }
        @Override public void onProviderDisabled(String provider) { }
    };

    private void openDingTalk(Prefs p) {
        try {
            Intent it = getPackageManager().getLaunchIntentForPackage("com.alibaba.android.rimet");
            if (it != null) {
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(it);
            } else {
                NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                if (nm != null) {
                    Notification n = new Notification.Builder(this, CHANNEL_ID)
                            .setSmallIcon(android.R.drawable.ic_dialog_info)
                            .setContentTitle("打卡助手")
                            .setContentText("到点且已在打卡点附近，但未安装钉钉，请手动打卡")
                            .build();
                    nm.notify(1002, n);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "open dingtalk failed", e);
        }
    }

    /**
     * 判断当前时间命中的打卡时段。
     * 返回 1=上班时段, 2=下班时段, 0=不在任何时段。
     */
    private int matchSlot(Prefs p) {
        String now = new SimpleDateFormat("HH:mm", Locale.US).format(new Date());
        if (inRange(now, p.getMorningStart(), p.getMorningEnd())) return 1;
        if (inRange(now, p.getEveningStart(), p.getEveningEnd())) return 2;
        return 0;
    }

    private boolean inRange(String now, String start, String end) {
        if (start == null || end == null || start.isEmpty() || end.isEmpty()) return false;
        if (start.equals(end)) return true; // 开始=结束视为全天
        if (start.compareTo(end) < 0) {
            return now.compareTo(start) >= 0 && now.compareTo(end) <= 0;
        } else {
            // 跨天时段（如 22:00 - 06:00）
            return now.compareTo(start) >= 0 || now.compareTo(end) <= 0;
        }
    }

    private float distanceMeters(float lat1, float lng1, double lat2, double lng2) {
        float[] res = new float[1];
        Location.distanceBetween(lat1, lng1, lat2, lng2, res);
        return res[0];
    }

    private void createChannel() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "打卡助手", NotificationManager.IMPORTANCE_LOW);
        ch.setDescription("打卡助手常驻运行状态");
        nm.createNotificationChannel(ch);
    }

    private Notification buildServiceNotification() {
        Intent open = new Intent(this, CheckInActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentTitle("打卡助手运行中")
                .setContentText("到达打卡点附近且移动时将自动打开钉钉")
                .setOngoing(true)
                .setContentIntent(pi)
                .build();
    }

    @Override
    public void onDestroy() {
        stopUpdates();
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
