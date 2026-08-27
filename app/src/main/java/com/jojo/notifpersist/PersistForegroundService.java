package com.jojo.notifpersist;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

public class PersistForegroundService extends Service {
    private static final String CHANNEL_ID = "persist";
    private static volatile boolean running = false;

    public static boolean isRunning() { return running; }

    @Override public void onCreate() {
        super.onCreate();
        running = true;
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "保活服务",
                    NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("保持通知续命服务在后台运行");
            nm.createNotificationChannel(ch);
        }
        Notification n = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("通知续命运行中")
                .setContentText("正在保持锁屏通知持久化")
                .setSmallIcon(android.R.drawable.ic_popup_sync)
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(1, n);
        }
    }

    @Override public void onDestroy() {
        running = false;
        super.onDestroy();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
