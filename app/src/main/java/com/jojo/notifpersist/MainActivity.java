package com.jojo.notifpersist;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;

public class MainActivity extends Activity {
    private TextView txtListener, txtDnd, txtBattery, txtFgs;
    private ImageView dotListener, dotDnd, dotBattery, dotFgs;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        txtListener = findViewById(R.id.txtListener);
        txtDnd = findViewById(R.id.txtDnd);
        txtBattery = findViewById(R.id.txtBattery);
        txtFgs = findViewById(R.id.txtFgs);
        dotListener = findViewById(R.id.dotListener);
        dotDnd = findViewById(R.id.dotDnd);
        dotBattery = findViewById(R.id.dotBattery);
        dotFgs = findViewById(R.id.dotFgs);

        Switch enableSwitch = findViewById(R.id.enableSwitch);
        enableSwitch.setChecked(Config.isEnabled(this));
        enableSwitch.setOnCheckedChangeListener((b, checked) -> Config.setEnabled(this, checked));

        Button btnListener = findViewById(R.id.btnListener);
        btnListener.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));

        Button btnDnd = findViewById(R.id.btnDnd);
        btnDnd.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)));

        Button btnBattery = findViewById(R.id.btnBattery);
        btnBattery.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= 23) {
                startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:" + getPackageName())));
            }
        });

        Button btnAutostart = findViewById(R.id.btnAutostart);
        btnAutostart.setOnClickListener(v -> openAutostart());

        Button btnStart = findViewById(R.id.btnStart);
        btnStart.setOnClickListener(v -> startFgs());

        startFgs();
    }

    private void openAutostart() {
        try {
            Intent i = new Intent("miui.intent.action.OP_AUTO_START");
            i.addCategory(Intent.CATEGORY_DEFAULT);
            startActivity(i);
        } catch (Exception e) {
            try {
                Intent i = new Intent();
                i.setClassName("com.miui.securitycenter",
                        "com.miui.securitycenter.autostart.AutoStartManagementActivity");
                startActivity(i);
            } catch (Exception e2) {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName())));
            }
        }
    }

    private void startFgs() {
        Context c = getApplicationContext();
        Intent i = new Intent(c, PersistForegroundService.class);
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
        else c.startService(i);
    }

    @Override protected void onResume() {
        super.onResume();
        ComponentName cn = new ComponentName(this, NotifListenerService.class);
        String flat = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        boolean listenerOk = flat != null && flat.contains(cn.flattenToString());
        NotificationManager nm = getSystemService(NotificationManager.class);
        boolean dndOk = nm.isNotificationPolicyAccessGranted();
        PowerManager pm = getSystemService(PowerManager.class);
        boolean battOk = pm.isIgnoringBatteryOptimizations(getPackageName());
        boolean fgsOk = PersistForegroundService.isRunning();

        setStatus(dotListener, txtListener, listenerOk, "已开启", "未开启");
        setStatus(dotDnd, txtDnd, dndOk, "已授权", "未授权");
        setStatus(dotBattery, txtBattery, battOk, "已忽略", "未忽略");
        setStatus(dotFgs, txtFgs, fgsOk, "运行中", "未运行");
    }

    private void setStatus(ImageView dot, TextView txt, boolean ok, String okText, String failText) {
        txt.setText(ok ? okText : failText);
        txt.setTextColor(getColor(ok ? R.color.ok_green : R.color.fail_red));
        dot.setImageResource(ok ? R.drawable.dot_green : R.drawable.dot_red);
    }
}
