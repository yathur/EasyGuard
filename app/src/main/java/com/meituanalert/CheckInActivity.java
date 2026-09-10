package com.meituanalert;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CheckInActivity extends Activity {
    private static final int REQ_PERMS = 100;

    private Prefs prefs;
    private Switch swCheckin;
    private TextView tvPoint;
    private EditText etRadius;
    private EditText etMorningStart;
    private EditText etMorningEnd;
    private EditText etMorningCount;
    private EditText etEveningStart;
    private EditText etEveningEnd;
    private EditText etEveningCount;
    private EditText etSpeed;
    private TextView tvServiceState;
    private TextView tvStateDot;
    private Button btnRadiusMinus, btnRadiusPlus;
    private Button btnMorningCountMinus, btnMorningCountPlus;
    private Button btnEveningCountMinus, btnEveningCountPlus;
    private Button btnSpeedMinus, btnSpeedPlus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_checkin);

        prefs = new Prefs(this);

        swCheckin = findViewById(R.id.sw_checkin);
        tvPoint = findViewById(R.id.tv_point);
        etRadius = findViewById(R.id.et_radius);
        etMorningStart = findViewById(R.id.et_morning_start);
        etMorningEnd = findViewById(R.id.et_morning_end);
        etMorningCount = findViewById(R.id.et_morning_count);
        etEveningStart = findViewById(R.id.et_evening_start);
        etEveningEnd = findViewById(R.id.et_evening_end);
        etEveningCount = findViewById(R.id.et_evening_count);
        etSpeed = findViewById(R.id.et_speed);
        tvServiceState = findViewById(R.id.tv_service_state);
        tvStateDot = findViewById(R.id.tv_state_dot);
        btnRadiusMinus = findViewById(R.id.btn_radius_minus);
        btnRadiusPlus = findViewById(R.id.btn_radius_plus);
        btnMorningCountMinus = findViewById(R.id.btn_morning_count_minus);
        btnMorningCountPlus = findViewById(R.id.btn_morning_count_plus);
        btnEveningCountMinus = findViewById(R.id.btn_evening_count_minus);
        btnEveningCountPlus = findViewById(R.id.btn_evening_count_plus);
        btnSpeedMinus = findViewById(R.id.btn_speed_minus);
        btnSpeedPlus = findViewById(R.id.btn_speed_plus);

        swCheckin.setChecked(prefs.isCheckinEnabled());
        etRadius.setText(String.valueOf(prefs.getRadius()));
        etMorningStart.setText(prefs.getMorningStart());
        etMorningEnd.setText(prefs.getMorningEnd());
        etMorningCount.setText(String.valueOf(prefs.getMorningCount()));
        etEveningStart.setText(prefs.getEveningStart());
        etEveningEnd.setText(prefs.getEveningEnd());
        etEveningCount.setText(String.valueOf(prefs.getEveningCount()));
        etSpeed.setText(String.valueOf(prefs.getSpeedThreshold()));
        refreshPointText();
        refreshServiceState();

        swCheckin.setOnCheckedChangeListener((b, isChecked) -> {
            prefs.setCheckinEnabled(isChecked);
            if (isChecked) {
                requestAllPermissions();
                startServiceSafe();
            } else {
                stopServiceSafe();
            }
            refreshServiceState();
        });

        Button btnSave = findViewById(R.id.btn_save_checkin);
        btnSave.setOnClickListener(v -> saveSettings());

        Button btnPoint = findViewById(R.id.btn_point);
        btnPoint.setOnClickListener(v -> useCurrentLocation());

        Button btnTestOpen = findViewById(R.id.btn_test_open);
        btnTestOpen.setOnClickListener(v -> testOpenDingTalk());

        Button btnPerms = findViewById(R.id.btn_perms);
        btnPerms.setOnClickListener(v -> requestAllPermissions());

        Button btnBattery = findViewById(R.id.btn_battery);
        btnBattery.setOnClickListener(v -> openBatterySettings());

        // 悬浮窗权限：Android 10+ 后台启动钉钉需要它；ColorOS 默认禁止，未授权时显示引导按钮
        Button btnOverlay = findViewById(R.id.btn_overlay);
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            btnOverlay.setVisibility(View.VISIBLE);
            btnOverlay.setOnClickListener(v -> openOverlaySettings());
        } else {
            btnOverlay.setVisibility(View.GONE);
        }

        // 数字调节按钮
        btnRadiusMinus.setOnClickListener(v -> adjustRadius(-10));
        btnRadiusPlus.setOnClickListener(v -> adjustRadius(10));
        btnMorningCountMinus.setOnClickListener(v -> adjustInt(etMorningCount, 1, 10, -1));
        btnMorningCountPlus.setOnClickListener(v -> adjustInt(etMorningCount, 1, 10, 1));
        btnEveningCountMinus.setOnClickListener(v -> adjustInt(etEveningCount, 1, 10, -1));
        btnEveningCountPlus.setOnClickListener(v -> adjustInt(etEveningCount, 1, 10, 1));
        btnSpeedMinus.setOnClickListener(v -> adjustSpeed(-0.5f));
        btnSpeedPlus.setOnClickListener(v -> adjustSpeed(0.5f));

        // 防漏：打卡开关开着但服务没在跑（如被系统清理），打开页面时自动重新拉起
        if (prefs.isCheckinEnabled()) {
            startServiceQuiet();
        }
    }

    private void saveSettings() {
        String mStart = etMorningStart.getText().toString().trim();
        String mEnd = etMorningEnd.getText().toString().trim();
        String eStart = etEveningStart.getText().toString().trim();
        String eEnd = etEveningEnd.getText().toString().trim();
        if (mStart.matches("\\d{2}:\\d{2}")) prefs.setMorningStart(mStart);
        if (mEnd.matches("\\d{2}:\\d{2}")) prefs.setMorningEnd(mEnd);
        if (eStart.matches("\\d{2}:\\d{2}")) prefs.setEveningStart(eStart);
        if (eEnd.matches("\\d{2}:\\d{2}")) prefs.setEveningEnd(eEnd);

        int mCount = parseCount(etMorningCount.getText().toString(), prefs.getMorningCount());
        int eCount = parseCount(etEveningCount.getText().toString(), prefs.getEveningCount());
        prefs.setMorningCount(mCount);
        prefs.setEveningCount(eCount);

        int radius;
        try {
            radius = Integer.parseInt(etRadius.getText().toString().trim());
            if (radius < 10) radius = 10;
            if (radius > 2000) radius = 2000;
        } catch (Exception e) {
            radius = prefs.getRadius();
        }
        prefs.setRadius(radius);

        float speed;
        try {
            speed = Float.parseFloat(etSpeed.getText().toString().trim());
            if (speed < 0f) speed = 0f; // 0 表示不要求移动，进半径即触发
        } catch (Exception e) {
            speed = prefs.getSpeedThreshold();
        }
        prefs.setSpeedThreshold(speed);

        etRadius.setText(String.valueOf(prefs.getRadius()));
        etMorningStart.setText(prefs.getMorningStart());
        etMorningEnd.setText(prefs.getMorningEnd());
        etMorningCount.setText(String.valueOf(prefs.getMorningCount()));
        etEveningStart.setText(prefs.getEveningStart());
        etEveningEnd.setText(prefs.getEveningEnd());
        etEveningCount.setText(String.valueOf(prefs.getEveningCount()));
        etSpeed.setText(String.valueOf(prefs.getSpeedThreshold()));
        refreshPointText();
        if (prefs.isCheckinEnabled()) restartServiceQuiet();
        Toast.makeText(this, "设置已保存，服务已用新配置重启", Toast.LENGTH_SHORT).show();
    }

    private void adjustRadius(int delta) {
        int v;
        try {
            v = Integer.parseInt(etRadius.getText().toString().trim());
        } catch (Exception e) {
            v = prefs.getRadius();
        }
        v += delta;
        v = Math.max(10, Math.min(2000, v));
        etRadius.setText(String.valueOf(v));
    }

    private void adjustInt(EditText et, int min, int max, int delta) {
        int v;
        try {
            v = Integer.parseInt(et.getText().toString().trim());
        } catch (Exception e) {
            v = min;
        }
        v += delta;
        v = Math.max(min, Math.min(max, v));
        et.setText(String.valueOf(v));
    }

    private void adjustSpeed(float delta) {
        float v;
        try {
            v = Float.parseFloat(etSpeed.getText().toString().trim());
        } catch (Exception e) {
            v = prefs.getSpeedThreshold();
        }
        v += delta;
        v = Math.max(0f, Math.min(10f, v));
        v = Math.round(v * 10f) / 10f;
        etSpeed.setText(String.valueOf(v));
    }

    private int parseCount(String s, int def) {
        try {
            int v = Integer.parseInt(s.trim());
            return Math.max(1, Math.min(10, v));
        } catch (Exception e) {
            return def;
        }
    }

    private boolean locatingPoint;
    private final Handler handler = new Handler(Looper.getMainLooper());

    /** 设置打卡点：主动请求最新定位（GPS 优先），6 秒超时兜底用已有位置，避免用到过期缓存 */
    private void useCurrentLocation() {
        requestAllPermissions();
        if (locatingPoint) return;
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "请先授予定位权限再设置打卡点", Toast.LENGTH_LONG).show();
            return;
        }
        final LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) return;
        locatingPoint = true;
        Toast.makeText(this, "正在获取最新定位，请稍候…", Toast.LENGTH_SHORT).show();

        final LocationListener single = new LocationListener() {
            @Override public void onLocationChanged(Location loc) {
                if (loc != null) {
                    try { lm.removeUpdates(this); } catch (Exception ignored) { }
                    applyPoint(loc);
                }
            }
            @Override public void onStatusChanged(String provider, int status, Bundle extras) { }
            @Override public void onProviderEnabled(String provider) { }
            @Override public void onProviderDisabled(String provider) { }
        };
        try {
            lm.requestSingleUpdate(LocationManager.GPS_PROVIDER, single, Looper.getMainLooper());
            lm.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, single, Looper.getMainLooper());
        } catch (Exception ignored) { }

        // 6 秒兜底：GPS 迟迟不出结果时用较新的已有位置
        handler.postDelayed(() -> {
            locatingPoint = false;
            try { lm.removeUpdates(single); } catch (Exception ignored) { }
            Location best = pickBestLocation(lm);
            if (best != null) {
                applyPoint(best);
            } else {
                Toast.makeText(this, "暂未获取到定位，请确认已开启定位并到空旷处重试",
                        Toast.LENGTH_LONG).show();
            }
        }, 6000);
    }

    private Location pickBestLocation(LocationManager lm) {
        Location best = null;
        try {
            for (String p : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
                Location l = lm.getLastKnownLocation(p);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            }
        } catch (Exception ignored) { }
        return best;
    }

    private void applyPoint(Location loc) {
        locatingPoint = false;
        prefs.setPoint((float) loc.getLatitude(), (float) loc.getLongitude());
        refreshPointText();
        if (prefs.isCheckinEnabled()) restartServiceQuiet();
        Toast.makeText(this, "打卡点已设为当前位置", Toast.LENGTH_SHORT).show();
    }

    private void refreshPointText() {
        if (prefs.hasPoint()) {
            tvPoint.setText("打卡点：(" + prefs.getLat() + ", " + prefs.getLng() + ")  半径 " + prefs.getRadius() + " 米");
        } else {
            tvPoint.setText("打卡点：未设置（点下方按钮用当前位置）");
        }
    }

    private void refreshServiceState() {
        String now = new SimpleDateFormat("HH:mm", Locale.US).format(new Date());
        String slotDesc;
        String countInfo = "";
        if (inSlot(now, prefs.getMorningStart(), prefs.getMorningEnd())) {
            slotDesc = "当前" + now + " 上班时段";
            countInfo = " · 今日上班已打卡 " + prefs.getMorningCountToday() + "/" + prefs.getMorningCount() + " 次";
        } else if (inSlot(now, prefs.getEveningStart(), prefs.getEveningEnd())) {
            slotDesc = "当前" + now + " 下班时段";
            countInfo = " · 今日下班已打卡 " + prefs.getEveningCountToday() + "/" + prefs.getEveningCount() + " 次";
        } else {
            slotDesc = "当前" + now + " 非打卡时段（时段外不定位，每5分钟检查时间）";
        }
        if (prefs.isCheckinEnabled()) {
            tvServiceState.setText("服务状态：已开启 · " + slotDesc + countInfo);
            tvServiceState.setTextColor(0xFF2E7D32);
            if (tvStateDot != null) tvStateDot.setTextColor(0xFF2E7D32);
        } else {
            tvServiceState.setText("服务状态：未开启 · " + slotDesc);
            tvServiceState.setTextColor(0xFFB71C1C);
            if (tvStateDot != null) tvStateDot.setTextColor(0xFFB71C1C);
        }
    }

    private boolean inSlot(String now, String start, String end) {
        if (start == null || end == null || start.isEmpty() || end.isEmpty()) return false;
        if (start.equals(end)) return true;
        if (start.compareTo(end) < 0) {
            return now.compareTo(start) >= 0 && now.compareTo(end) <= 0;
        } else {
            return now.compareTo(start) >= 0 || now.compareTo(end) <= 0;
        }
    }

    /** 测试链路：直接尝试打开钉钉（前台点击不受后台限制，用于验证包名与安装状态） */
    private void testOpenDingTalk() {
        try {
            Intent it = getPackageManager().getLaunchIntentForPackage("com.alibaba.android.rimet");
            if (it != null) {
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(it);
                Toast.makeText(this, "钉钉已尝试打开", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "未检测到钉钉，请先安装", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "打开失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void openOverlaySettings() {
        try {
            Intent it = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(it);
        } catch (Exception e) {
            Toast.makeText(this, "请在系统设置 → 应用 → 本应用 → 显示在其他应用上层 中允许",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void requestAllPermissions() {
        List<String> need = new ArrayList<>();
        String[] perms = new String[]{
                Manifest.permission.POST_NOTIFICATIONS,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
        };
        for (String p : perms) {
            if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) {
                need.add(p);
            }
        }
        if (!need.isEmpty()) {
            requestPermissions(need.toArray(new String[0]), REQ_PERMS);
        } else {
            requestBackgroundLocation();
        }
    }

    /** 后台定位单独申请：Android 10+ 需要用户选"始终允许"，通常要去系统设置操作 */
    private void requestBackgroundLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION}, REQ_PERMS);
        } else {
            Toast.makeText(this, "所需权限已全部授予", Toast.LENGTH_SHORT).show();
        }
    }

    private void startServiceSafe() {
        try {
            Intent it = new Intent(this, CheckInService.class);
            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(it);
            } else {
                startService(it);
            }
            Toast.makeText(this, "打卡服务已启动", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "服务启动失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
        refreshServiceState();
    }

    private void startServiceQuiet() {
        try {
            Intent it = new Intent(this, CheckInService.class);
            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(it);
            } else {
                startService(it);
            }
        } catch (Exception ignored) { }
        refreshServiceState();
    }

    /** 配置变更后重启服务，让新配置立即生效（静默，无 Toast） */
    private void restartServiceQuiet() {
        stopServiceSafe();
        startServiceQuiet();
    }

    private void stopServiceSafe() {
        try {
            stopService(new Intent(this, CheckInService.class));
        } catch (Exception ignored) { }
    }

    private void openBatterySettings() {
        try {
            Intent it = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName()));
            startActivity(it);
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (Exception e2) {
                Toast.makeText(this, "请在系统设置中为本应用关闭电池优化", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_PERMS || permissions == null || grantResults == null) return;
        List<String> denied = new ArrayList<>();
        for (int i = 0; i < permissions.length && i < grantResults.length; i++) {
            if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                denied.add(permissionLabel(permissions[i]));
            }
        }
        if (!denied.isEmpty()) {
            StringBuilder sb = new StringBuilder("未授权：");
            for (String d : denied) sb.append(d).append("、");
            sb.setLength(sb.length() - 1);
            if (denied.contains("后台定位")) {
                sb.append("。后台定位需手动开启：系统设置 → 应用 → 本应用 → 权限 → 位置 → 选“始终允许”");
            } else if (denied.contains("定位")) {
                sb.append("。可点“打开应用权限设置”手动开启；若不再弹窗也请到那里操作");
            } else {
                sb.append("，请重新点击申请");
            }
            Toast.makeText(this, sb.toString(), Toast.LENGTH_LONG).show();
        } else {
            // 后台定位未在本次授予（仅允许使用时），引导去设置
            if (checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "为让后台也能定位，请到系统设置将定位改为“始终允许”",
                        Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "权限已全部授予", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private String permissionLabel(String p) {
        switch (p) {
            case Manifest.permission.POST_NOTIFICATIONS:
                return "通知";
            case Manifest.permission.ACCESS_FINE_LOCATION:
            case Manifest.permission.ACCESS_COARSE_LOCATION:
                return "定位";
            case Manifest.permission.ACCESS_BACKGROUND_LOCATION:
                return "后台定位";
            default:
                return p;
        }
    }

    private void openAppSettings() {
        try {
            Intent it = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName()));
            startActivity(it);
        } catch (Exception e) {
            Toast.makeText(this, "请到系统设置 → 应用 → 本应用 → 权限 中手动开启", Toast.LENGTH_LONG).show();
        }
    }
}
