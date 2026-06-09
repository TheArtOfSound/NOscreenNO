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
    private static final String CHANNEL_ID = "qev_privacy_overlay";
    private static final int NOTIFICATION_ID = 91;
    private WindowManager windowManager;
    private View overlay;

    @Override public void onCreate() {
        super.onCreate();
        startForeground(NOTIFICATION_ID, notification());
        windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        overlay = new NoiseOverlay(this);

        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                flags,
                android.graphics.PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        windowManager.addView(overlay, params);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override public void onDestroy() {
        if (windowManager != null && overlay != null) {
            try { windowManager.removeView(overlay); } catch (Exception ignored) {}
        }
        overlay = null;
        windowManager = null;
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    private Notification notification() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "QEV Shield", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Runs the touch-through visual privacy shield.");
            nm.createNotificationChannel(ch);
        }
        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return b.setContentTitle("QEV Shield active")
                .setContentText("Touch-through visual mask is running. Return to QEV Shield to stop it.")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setOngoing(true)
                .build();
    }

    static class NoiseOverlay extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int frame = 0;

        NoiseOverlay(Context c) {
            super(c);
            setAlpha(0.74f);
        }

        @Override protected void onDraw(Canvas c) {
            int w = getWidth();
            int h = getHeight();
            frame++;
            c.drawColor(Color.argb(86, 0, 0, 0));

            p.setStrokeWidth(2.5f);
            for (int y = -40; y < h + 40; y += 16) {
                int wave = ((y + frame * 7) % 37) - 18;
                int a = (y % 48 == 0) ? 210 : 120;
                p.setColor(Color.argb(a, 0, 229, 168));
                c.drawLine(0, y, w, y + wave, p);
            }

            p.setTextSize(24f);
            p.setFakeBoldText(true);
            for (int y = 44; y < h; y += 92) {
                p.setColor(Color.argb(155, 255, 255, 255));
                c.drawText("QEV SHIELD  ///  PRIVATE VISUAL FIELD", 18 + (frame % 19), y, p);
            }
            p.setFakeBoldText(false);
            postInvalidateDelayed(90);
        }
    }
}
