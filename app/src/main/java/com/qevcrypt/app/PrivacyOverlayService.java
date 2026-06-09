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
        overlay = new PrivacyGlassOverlay(this);

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
                .setContentText("Subtle privacy glass is running. Return to QEV Shield to stop it.")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setOngoing(true)
                .build();
    }

    static class PrivacyGlassOverlay extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int frame = 0;

        PrivacyGlassOverlay(Context c) {
            super(c);
            setAlpha(0.46f);
        }

        @Override protected void onDraw(Canvas c) {
            int w = getWidth();
            int h = getHeight();
            frame++;

            // Soft privacy tint: readable to the user, harder for shoulder-surfing/cameras.
            c.drawColor(Color.argb(34, 0, 0, 0));

            // Sparse scan lines instead of full debug-wall effect.
            p.setStrokeWidth(1.2f);
            p.setColor(Color.argb(82, 0, 229, 168));
            for (int y = (frame % 24); y < h; y += 24) {
                c.drawLine(0, y, w, y, p);
            }

            // Subtle diagonal glass grain.
            p.setStrokeWidth(0.9f);
            p.setColor(Color.argb(38, 255, 255, 255));
            for (int x = -h; x < w; x += 92) {
                c.drawLine(x, h, x + h, 0, p);
            }

            // One small watermark, not repeated across the entire phone.
            p.setTextSize(20f);
            p.setFakeBoldText(true);
            p.setColor(Color.argb(135, 255, 255, 255));
            c.drawText("QEV Shield active", 22, h - 34, p);
            p.setFakeBoldText(false);

            postInvalidateDelayed(140);
        }
    }
}
