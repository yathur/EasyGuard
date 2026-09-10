package com.meituanalert;

import android.content.Context;
import android.content.SharedPreferences;

public class Prefs {
    private static final String FILE = "meituan_alert";
    private final SharedPreferences sp;

    public Prefs(Context c) {
        sp = c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    // ---- 提醒音量 ----
    public boolean isVolumeConfigured() { return sp.contains("alarm_volume"); }
    public float getAlarmVolume() { return sp.getFloat("alarm_volume", 1.0f); }
    public void setAlarmVolume(float v) {
        float vol = Math.max(0f, Math.min(1f, v));
        sp.edit().putFloat("alarm_volume", vol).apply();
    }

    // ---- 定时提醒 ----
    public boolean isFixedEnabled() { return sp.getBoolean("fixed_enabled", false); }
    public void setFixedEnabled(boolean v) { sp.edit().putBoolean("fixed_enabled", v).apply(); }

    public String getFixedTime() { return sp.getString("fixed_time", "09:00"); }
    public void setFixedTime(String v) { sp.edit().putString("fixed_time", v).apply(); }

    public long getCountdownAt() { return sp.getLong("countdown_at", 0); }
    public void setCountdownAt(long v) { sp.edit().putLong("countdown_at", v).apply(); }

    public boolean isCountdownEnabled() { return sp.getBoolean("countdown_enabled", false); }
    public void setCountdownEnabled(boolean v) { sp.edit().putBoolean("countdown_enabled", v).apply(); }

    public int getCountdownMinutes() { return sp.getInt("countdown_minutes", 60); }
    public void setCountdownMinutes(int v) { sp.edit().putInt("countdown_minutes", v).apply(); }

    // ---- 打卡助手 ----
    public boolean isCheckinEnabled() { return sp.getBoolean("checkin_enabled", false); }
    public void setCheckinEnabled(boolean v) { sp.edit().putBoolean("checkin_enabled", v).apply(); }

    public boolean hasPoint() { return sp.getFloat("lat", 0f) != 0f || sp.getFloat("lng", 0f) != 0f; }
    public float getLat() { return sp.getFloat("lat", 0f); }
    public float getLng() { return sp.getFloat("lng", 0f); }
    public void setPoint(float lat, float lng) {
        sp.edit().putFloat("lat", lat).putFloat("lng", lng).apply();
    }

    public int getRadius() { return sp.getInt("radius", 100); }
    public void setRadius(int v) { sp.edit().putInt("radius", v).apply(); }

    public String getMorningStart() { return sp.getString("morning_start", "07:00"); }
    public void setMorningStart(String v) { sp.edit().putString("morning_start", v).apply(); }

    public String getMorningEnd() { return sp.getString("morning_end", "09:00"); }
    public void setMorningEnd(String v) { sp.edit().putString("morning_end", v).apply(); }

    public int getMorningCount() { return sp.getInt("morning_count", 1); }
    public void setMorningCount(int v) { sp.edit().putInt("morning_count", Math.max(1, v)).apply(); }

    public String getEveningStart() { return sp.getString("evening_start", "17:30"); }
    public void setEveningStart(String v) { sp.edit().putString("evening_start", v).apply(); }

    public String getEveningEnd() { return sp.getString("evening_end", "21:00"); }
    public void setEveningEnd(String v) { sp.edit().putString("evening_end", v).apply(); }

    public int getEveningCount() { return sp.getInt("evening_count", 1); }
    public void setEveningCount(int v) { sp.edit().putInt("evening_count", Math.max(1, v)).apply(); }

    public float getSpeedThreshold() { return sp.getFloat("speed_threshold", 1.0f); }
    public void setSpeedThreshold(float v) { sp.edit().putFloat("speed_threshold", v).apply(); }

    public String getTriggeredDate() { return sp.getString("triggered_date", ""); }
    public void setTriggeredDate(String v) { sp.edit().putString("triggered_date", v).apply(); }

    public int getMorningCountToday() { return sp.getInt("morning_count_today", 0); }
    public void setMorningCountToday(int v) { sp.edit().putInt("morning_count_today", v).apply(); }

    public int getEveningCountToday() { return sp.getInt("evening_count_today", 0); }
    public void setEveningCountToday(int v) { sp.edit().putInt("evening_count_today", v).apply(); }
}
