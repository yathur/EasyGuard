package com.meituanalert;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null && Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Prefs p = new Prefs(context);
            if (p.isCheckinEnabled()) {
                try {
                    context.startForegroundService(new Intent(context, CheckInService.class));
                    Log.i("BootReceiver", "service restarted after boot");
                } catch (Exception e) {
                    Log.e("BootReceiver", "start failed", e);
                }
            }
        }
    }
}
