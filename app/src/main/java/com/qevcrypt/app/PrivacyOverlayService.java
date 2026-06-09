package com.qevcrypt.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

public class PrivacyOverlayService extends Service {
    private WindowManager windowManager;
    private View overlay;
    private static final String CHANNEL_ID = "qev_privacy_overlay";

    @Override public void onCreate() {
        super.onCreate();
        startForeground(91, notification());
        windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        overlay = new NoiseOverlay(this);
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                android.graphics.PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        windowManager.addView(overlay, params);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) { return START_STICKY; }

    @Override public void onDestroy() {
        if (windowManager != null && overlay != null) windowManager.removeView(overlay);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    private Notification notification() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "QEV Privacy Shield", NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(ch);
        }
        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        return b.setContentTitle("QEV Privacy Shield active")
                .setContentText("Visual scramble overlay is masking the screen.")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .build();
    }

    static class NoiseOverlay extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        NoiseOverlay(Context c) { super(c); setAlpha(0.86f); }

        @Override protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            c.drawColor(Color.argb(118, 0, 0, 0));
            p.setStrokeWidth(3f);
            for (int y = 0; y < h; y += 14) {
                int a = (y % 42 == 0) ? 210 : 135;
                p.setColor(Color.argb(a, 0, 229, 168));
                c.drawLine(0, y, w, y + ((y % 5) - 2) * 6, p);
            }
            p.setTextSize(28f);
            p.setFakeBoldText(true);
            for (int y = 50; y < h; y += 82) {
                p.setColor(Color.argb(185, 255, 255, 255));
                c.drawText("QEV ███ ENCRYPTED VISUAL FIELD ███", 22, y, p);
            }
            p.setFakeBoldText(false);
            postInvalidateDelayed(120);
        }
    }
}
