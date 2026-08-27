package com.jojo.notifpersist;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) && Config.isEnabled(context)) {
            Intent i = new Intent(context, PersistForegroundService.class);
            if (android.os.Build.VERSION.SDK_INT >= 26) context.startForegroundService(i);
            else context.startService(i);
        }
    }
}
