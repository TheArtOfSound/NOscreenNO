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
import android.graphics.Typeface;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

import java.util.Random;

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
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "noscreeno", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Runs the touch-through visual privacy shield.");
            nm.createNotificationChannel(ch);
        }
        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return b.setContentTitle("noscreeno shield active")
                .setContentText("Screen encrypted. Return to noscreeno to stop it.")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setOngoing(true)
                .build();
    }

    static class PrivacyGlassOverlay extends View {
        // Qira cyan accent.
        private static final int ACCENT = 0xFF34D8F0;
        // Glyph palette: hex + a few base64-ish symbols read as "ciphertext".
        private static final char[] GLYPHS = {
                '0','1','2','3','4','5','6','7','8','9',
                'A','B','C','D','E','F',
                'a','b','c','d','e','f',
                '/','+','=','#','%','$','&','*','x'
        };

        private final Paint glyphPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint labelBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Random rnd = new Random();

        private int frame = 0;

        // Per-column state, computed in onSizeChanged (no per-frame allocation).
        private float cellW;        // column width / glyph cell width
        private float cellH;        // row height
        private int cols;           // number of columns
        private int rows;           // number of glyph rows that fit (plus margin)
        private float[] headY;      // current head pixel position of each column's stream
        private float[] speed;      // fall speed (px per frame) per column
        private char[][] glyphs;    // the glyph grid [col][row]
        private long[] reseed;      // frame countdown until a column cycles glyphs

        PrivacyGlassOverlay(Context c) {
            super(c);
            // Heavy obscuring layer overall. The near-black fill below plus this
            // alpha keep onlookers from reading anything behind the shield.
            setAlpha(0.92f);

            glyphPaint.setTypeface(Typeface.MONOSPACE);
            labelPaint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
            labelBgPaint.setStyle(Paint.Style.FILL);
        }

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            super.onSizeChanged(w, h, ow, oh);
            buildColumns(w, h);
        }

        private void buildColumns(int w, int h) {
            if (w <= 0 || h <= 0) return;

            // Cell sizing scales a little with screen width so it looks dense
            // on phones and tablets alike.
            cellW = Math.max(16f, w / 34f);
            cellH = cellW * 1.32f;
            glyphPaint.setTextSize(cellW * 1.04f);

            cols = (int) Math.ceil(w / cellW);
            rows = (int) Math.ceil(h / cellH) + 2;

            headY = new float[cols];
            speed = new float[cols];
            reseed = new long[cols];
            glyphs = new char[cols][rows];

            for (int x = 0; x < cols; x++) {
                headY[x] = -rnd.nextInt(rows) * cellH;         // staggered starts
                speed[x] = cellH * (0.5f + rnd.nextFloat());   // varied fall rate
                reseed[x] = 2 + rnd.nextInt(6);
                for (int y = 0; y < rows; y++) {
                    glyphs[x][y] = GLYPHS[rnd.nextInt(GLYPHS.length)];
                }
            }
        }

        @Override protected void onDraw(Canvas c) {
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;
            if (glyphs == null || cols == 0) buildColumns(w, h);
            if (glyphs == null) return;

            frame++;

            // 1) Heavy near-black base so the screen content is genuinely hidden.
            c.drawColor(0xFF05070A);
            // Faint cyan wash to tint the whole shield.
            c.drawColor(0x14000000 | (ACCENT & 0x00FFFFFF));

            // 2) Animated ciphertext columns.
            for (int x = 0; x < cols; x++) {
                float colX = x * cellW + cellW * 0.12f;

                // Advance and wrap the falling head for this column.
                headY[x] += speed[x];
                if (headY[x] > h + cellH) {
                    headY[x] = -rnd.nextInt(rows) * cellH;
                    speed[x] = cellH * (0.5f + rnd.nextFloat());
                }

                // Periodically mutate one glyph in the column so the stream
                // keeps "churning" like live ciphertext (cheap: 1 char/col).
                if (--reseed[x] <= 0) {
                    glyphs[x][rnd.nextInt(rows)] = GLYPHS[rnd.nextInt(GLYPHS.length)];
                    reseed[x] = 2 + rnd.nextInt(6);
                }

                int headRow = (int) Math.floor(headY[x] / cellH);
                for (int y = 0; y < rows; y++) {
                    float gy = y * cellH + cellH;       // baseline of this row
                    if (gy < -cellH || gy > h + cellH) continue;

                    int dist = headRow - y;             // rows behind the bright head
                    int alpha;
                    int col;
                    if (dist == 0) {
                        // Bright leading glyph (near white-cyan).
                        col = 0xFFEAFEFF;
                        alpha = 255;
                    } else if (dist > 0 && dist < 18) {
                        // Fading cyan trail behind the head.
                        alpha = 235 - dist * 13;
                        if (alpha < 28) alpha = 28;
                        col = ACCENT;
                    } else {
                        // Dim ambient ciphertext filling the rest of the screen.
                        alpha = 46;
                        col = ACCENT;
                    }

                    glyphPaint.setColor((col & 0x00FFFFFF) | (alpha << 24));
                    c.drawText(String.valueOf(glyphs[x][y]), colX, gy, glyphPaint);
                }
            }

            // 3) Scanlines over the top to reinforce the "encrypted display" feel.
            linePaint.setStrokeWidth(1f);
            linePaint.setColor(0x1A000000 | (ACCENT & 0x00FFFFFF));
            for (int y = (frame % 6); y < h; y += 6) {
                c.drawLine(0, y, w, y, linePaint);
            }

            // 4) Centered status label so it's clear noscreeno is active.
            drawLabel(c, w, h);

            // Cap redraw rate (~100ms) to stay performant + touch-through.
            postInvalidateDelayed(100);
        }

        private void drawLabel(Canvas c, int w, int h) {
            String text = "🔒 noscreeno — screen encrypted";
            float ts = Math.max(28f, w / 22f);
            labelPaint.setTextSize(ts);

            float tw = labelPaint.measureText(text);
            Paint.FontMetrics fm = labelPaint.getFontMetrics();
            float th = fm.descent - fm.ascent;

            float cx = w / 2f;
            float cy = h / 2f;
            float padX = ts * 0.9f;
            float padY = ts * 0.6f;

            // Rounded backing plate for legibility against the busy ciphertext.
            labelBgPaint.setColor(0xE6080C12);
            c.drawRoundRect(
                    cx - tw / 2f - padX, cy - th / 2f - padY,
                    cx + tw / 2f + padX, cy + th / 2f + padY,
                    ts * 0.4f, ts * 0.4f, labelBgPaint);

            // Cyan border.
            labelBgPaint.setColor(ACCENT);
            labelBgPaint.setStyle(Paint.Style.STROKE);
            labelBgPaint.setStrokeWidth(Math.max(2f, ts * 0.06f));
            c.drawRoundRect(
                    cx - tw / 2f - padX, cy - th / 2f - padY,
                    cx + tw / 2f + padX, cy + th / 2f + padY,
                    ts * 0.4f, ts * 0.4f, labelBgPaint);
            labelBgPaint.setStyle(Paint.Style.FILL);

            labelPaint.setColor(0xFFEAFEFF);
            float baseline = cy - (fm.ascent + fm.descent) / 2f;
            c.drawText(text, cx - tw / 2f, baseline, labelPaint);
        }
    }
}
