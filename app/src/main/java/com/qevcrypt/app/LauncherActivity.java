package com.qevcrypt.app;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class LauncherActivity extends Activity {
    private LinearLayout appList;
    private EditText search;
    private final List<AppEntry> apps = new ArrayList<>();

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Window w = getWindow();
        w.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        loadApps();
        buildUi();
        renderApps("");
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(28, 32, 28, 48);
        root.setBackgroundColor(Color.rgb(5, 7, 12));
        scroll.addView(root);

        TextView title = text("QEV Home", 32, true, Color.WHITE);
        root.addView(title);
        root.addView(text("Private launcher shell. Launch apps, open encrypted composer, and start the visual shield from one place.", 14, false, Color.rgb(188, 196, 204)));

        LinearLayout quick = row();
        Button composer = btn("Encrypted composer");
        Button shield = btn("Start shield");
        quick.addView(composer, weight());
        quick.addView(shield, weight());
        root.addView(quick);
        composer.setOnClickListener(v -> startActivity(new Intent(this, MainActivity.class)));
        shield.setOnClickListener(v -> startShield());

        search = new EditText(this);
        search.setHint("Search apps...");
        search.setTextColor(Color.WHITE);
        search.setHintTextColor(Color.rgb(130, 142, 154));
        root.addView(search, full());
        search.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { renderApps(s.toString()); }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });

        appList = new LinearLayout(this);
        appList.setOrientation(LinearLayout.VERTICAL);
        root.addView(appList);
        setContentView(scroll);
    }

    private void loadApps() {
        PackageManager pm = getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN, null);
        main.addCategory(Intent.CATEGORY_LAUNCHER);
        List<android.content.pm.ResolveInfo> infos = pm.queryIntentActivities(main, 0);
        for (android.content.pm.ResolveInfo info : infos) {
            String label = info.loadLabel(pm).toString();
            String pkg = info.activityInfo.packageName;
            String cls = info.activityInfo.name;
            if (pkg.equals(getPackageName()) && cls.endsWith("LauncherActivity")) continue;
            apps.add(new AppEntry(label, pkg, cls));
        }
        Collections.sort(apps, Comparator.comparing(a -> a.label.toLowerCase()));
    }

    private void renderApps(String q) {
        if (appList == null) return;
        appList.removeAllViews();
        String needle = q == null ? "" : q.trim().toLowerCase();
        int shown = 0;
        for (AppEntry app : apps) {
            if (!needle.isEmpty() && !app.label.toLowerCase().contains(needle) && !app.pkg.toLowerCase().contains(needle)) continue;
            Button b = btn(app.label);
            b.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            b.setOnClickListener(v -> launch(app));
            appList.addView(b, full());
            shown++;
            if (shown >= 80) break;
        }
        if (shown == 0) appList.addView(text("No matching apps.", 14, false, Color.rgb(188, 196, 204)));
    }

    private void launch(AppEntry app) {
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_LAUNCHER);
        i.setClassName(app.pkg, app.cls);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { startActivity(i); } catch (Exception e) { }
    }

    private void startShield() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:" + getPackageName())));
            return;
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) startForegroundService(new Intent(this, PrivacyOverlayService.class));
        else startService(new Intent(this, PrivacyOverlayService.class));
    }

    private TextView text(String s, int sp, boolean bold, int color) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(sp);
        v.setTextColor(color);
        v.setPadding(0, 8, 0, 12);
        if (bold) v.setTypeface(Typeface.DEFAULT_BOLD);
        return v;
    }

    private Button btn(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        return b;
    }

    private LinearLayout row() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); return l; }
    private LinearLayout.LayoutParams full() { return new LinearLayout.LayoutParams(-1, -2); }
    private LinearLayout.LayoutParams weight() { return new LinearLayout.LayoutParams(0, -2, 1); }

    static class AppEntry {
        final String label;
        final String pkg;
        final String cls;
        AppEntry(String label, String pkg, String cls) { this.label = label; this.pkg = pkg; this.cls = cls; }
    }
}
