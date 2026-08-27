package com.jojo.notifpersist;

import android.content.Context;
import android.content.SharedPreferences;

public class Config {
    private static final String PREFS = "notifpersist";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_GAP_MS = "gap_ms";

    public static boolean isEnabled(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true);
    }
    public static void setEnabled(Context c, boolean v) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, v).apply();
    }
    public static long getGapMs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_GAP_MS, 5);
    }
}
