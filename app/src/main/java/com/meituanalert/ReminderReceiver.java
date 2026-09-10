package com.meituanalert;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ReminderReceiver extends BroadcastReceiver {

    public static final String EXTRA_TYPE = "type";
    public static final String TYPE_FIXED = "fixed";
    public static final String TYPE_COUNTDOWN = "countdown";

    @Override
    public void onReceive(Context context, Intent intent) {
        String type = intent == null ? null : intent.getStringExtra(EXTRA_TYPE);
        if (TYPE_FIXED.equals(type)) {
            String time = new Prefs(context).getFixedTime();
            Notifier.fire(context, "美团提醒：该锁车了", "固定提醒",
                    "已到设定时间 " + time + "，检查一下单车是否锁好，避免超时扣费");
            // 每天提醒：本次触发后重排明天
            Prefs p = new Prefs(context);
            if (p.isFixedEnabled()) {
                ReminderScheduler.scheduleFixed(context, p);
            }
        } else if (TYPE_COUNTDOWN.equals(type)) {
            int minutes = intent.getIntExtra("minutes", 0);
            Prefs p = new Prefs(context);
            p.setCountdownAt(0);
            p.setCountdownEnabled(false); // 一次性：触发后关闭开关
            Notifier.fire(context, "美团提醒：骑行倒计时结束", "倒计时提醒",
                    "已过去 " + minutes + " 分钟，记得锁车，避免超时扣费");
        }
    }
}
