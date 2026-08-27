package com.jojo.notifpersist;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 通知续命核心服务
 *  - 锁屏瞬间开启 DND 亮屏压制窗口（允许所有类别，仅压制 SCREEN_OFF/PEEK 等视觉）
 *  - 链式 snooze：按 when 升序，每 gapMs（默认 5ms）snooze 一条（时长 400ms）
 *    → 重投递时间天然递增，锁屏按时间顺序显示最新在上；避免批量调用被系统合并
 *  - 跳过分组摘要（尤其系统自动分组），避免整组被系统一起处理导致乱序
 *  - 每个解锁周期只刷新一次（needsRefresh），防止循环亮屏
 */
public class NotifListenerService extends NotificationListenerService {
    private static final String TAG = "NotifPersist";
    private static final long SNOOZE_MS = 400;
    private static final long DND_WINDOW_MS = 5000;

    private static class Entry {
        final String pkg;
        final boolean ongoing;
        final boolean groupSummary;
        final long when;
        Entry(String pkg, boolean ongoing, boolean groupSummary, long when) {
            this.pkg = pkg; this.ongoing = ongoing; this.groupSummary = groupSummary; this.when = when;
        }
    }

    private final Map<String, Entry> active = new HashMap<>();
    private volatile boolean needsRefresh = true;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable snoozeChain;
    private int savedFilter = NotificationManager.INTERRUPTION_FILTER_ALL;
    private NotificationManager.Policy savedPolicy;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                onScreenOff();
            } else if (Intent.ACTION_USER_PRESENT.equals(action)) {
                needsRefresh = true;
                restoreDnd();
            }
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_USER_PRESENT);
        registerReceiver(receiver, f);
    }

    @Override public void onDestroy() {
        try { unregisterReceiver(receiver); } catch (Exception ignored) {}
        handler.removeCallbacksAndMessages(null);
        restoreDnd();
        super.onDestroy();
    }

    @Override public void onListenerConnected() {
        Log.i(TAG, "listener connected");
        active.clear();
        for (StatusBarNotification sbn : getActiveNotifications()) track(sbn);
        try {
            startForegroundService(new Intent(this, PersistForegroundService.class));
        } catch (Exception e) { Log.w(TAG, "fgs: " + e.getMessage()); }
    }

    @Override public void onListenerDisconnected() {
        try { requestRebind(new ComponentName(this, NotifListenerService.class)); } catch (Exception ignored) {}
    }

    @Override public void onNotificationPosted(StatusBarNotification sbn) { track(sbn); }
    @Override public void onNotificationRemoved(StatusBarNotification sbn, RankingMap rankingMap) {
        active.remove(sbn.getKey());
    }

    private void track(StatusBarNotification sbn) {
        Notification n = sbn.getNotification();
        boolean ongoing = (n.flags & Notification.FLAG_ONGOING_EVENT) != 0;
        boolean groupSummary = (n.flags & Notification.FLAG_GROUP_SUMMARY) != 0;
        long when = n.when;
        if (when <= 0) when = sbn.getPostTime();
        active.put(sbn.getKey(), new Entry(sbn.getPackageName(), ongoing, groupSummary, when));
    }

    private void onScreenOff() {
        if (!Config.isEnabled(this)) {
            Log.i(TAG, "disabled, skip");
            return;
        }
        if (!needsRefresh) {
            Log.i(TAG, "SCREEN_OFF skipped");
            return;
        }
        needsRefresh = false;

        // DND 必须立刻开启，覆盖最早的重投递
        enableDndWindow();

        List<Map.Entry<String, Entry>> list = new ArrayList<>(active.entrySet());
        list.sort(Comparator.comparingLong(e -> e.getValue().when));

        final List<String> keys = new ArrayList<>();
        for (Map.Entry<String, Entry> e : list) {
            Entry en = e.getValue();
            if (en.ongoing || en.pkg.equals(getPackageName())) continue;
            if (en.groupSummary) continue; // 跳过分组摘要，避免整组乱序
            keys.add(e.getKey());
        }
        final long gap = Config.getGapMs(this);
        Log.i(TAG, "SCREEN_OFF: chain-snoozing " + keys.size() + " notifications, gap " + gap + "ms");

        handler.removeCallbacks(snoozeChain);
        snoozeChain = new Runnable() {
            int i = 0;
            @Override public void run() {
                if (i >= keys.size()) {
                    Log.i(TAG, "snooze chain finished");
                    return;
                }
                String key = keys.get(i);
                i++;
                try {
                    snoozeNotification(key, SNOOZE_MS);
                } catch (Exception ex) {
                    Log.w(TAG, "snooze failed: " + key + " " + ex.getMessage());
                }
                handler.postDelayed(this, gap);
            }
        };
        handler.post(snoozeChain);
    }

    private void enableDndWindow() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (!nm.isNotificationPolicyAccessGranted()) {
            Log.w(TAG, "DND access not granted");
            return;
        }
        try {
            savedFilter = nm.getCurrentInterruptionFilter();
            savedPolicy = nm.getNotificationPolicy();
            int categories = NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS
                    | NotificationManager.Policy.PRIORITY_CATEGORY_CALLS
                    | NotificationManager.Policy.PRIORITY_CATEGORY_MESSAGES
                    | NotificationManager.Policy.PRIORITY_CATEGORY_REMINDERS
                    | NotificationManager.Policy.PRIORITY_CATEGORY_EVENTS
                    | NotificationManager.Policy.PRIORITY_CATEGORY_SYSTEM
                    | NotificationManager.Policy.PRIORITY_CATEGORY_MEDIA;
            int suppressed = NotificationManager.Policy.SUPPRESSED_EFFECT_SCREEN_OFF
                    | NotificationManager.Policy.SUPPRESSED_EFFECT_PEEK
                    | NotificationManager.Policy.SUPPRESSED_EFFECT_LIGHTS
                    | NotificationManager.Policy.SUPPRESSED_EFFECT_FULL_SCREEN_INTENT;
            NotificationManager.Policy p = new NotificationManager.Policy(categories,
                    NotificationManager.Policy.PRIORITY_SENDERS_ANY,
                    NotificationManager.Policy.PRIORITY_SENDERS_ANY,
                    suppressed);
            nm.setNotificationPolicy(p);
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY);
            handler.removeCallbacks(restoreRunnable);
            handler.postDelayed(restoreRunnable, DND_WINDOW_MS);
            Log.i(TAG, "DND window enabled for " + DND_WINDOW_MS + "ms");
        } catch (Exception e) {
            Log.w(TAG, "DND window failed: " + e.getMessage());
        }
    }

    private final Runnable restoreRunnable = this::restoreDnd;

    private void restoreDnd() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        try {
            nm.setInterruptionFilter(savedFilter);
            if (savedPolicy != null) nm.setNotificationPolicy(savedPolicy);
        } catch (Exception e) {
            Log.w(TAG, "DND restore failed: " + e.getMessage());
        }
        savedPolicy = null;
    }
}
