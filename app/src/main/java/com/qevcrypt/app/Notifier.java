package com.qevcrypt.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/**
 * Posts a notification when an encrypted message arrives, so you actually know.
 * Privacy-respecting: it names the sender but never shows the message body
 * (the body is sealed and only decrypts after you unlock).
 */
public final class Notifier {

    private static final String CHANNEL = "noscreeno_messages";

    private Notifier() {}

    public static void newMessage(Context c, String sender) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Messages", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("New encrypted messages");
            nm.createNotificationChannel(ch);
        }
        Intent open = new Intent(c, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT
                | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        PendingIntent pi = PendingIntent.getActivity(c, 0, open, piFlags);

        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(c, CHANNEL)
                : new Notification.Builder(c);
        Notification n = b
                .setSmallIcon(android.R.drawable.ic_dialog_email)
                .setContentTitle(sender == null || sender.trim().isEmpty() ? "New message" : "Message from " + sender)
                .setContentText("Unlock noscreeno to read")
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build();
        nm.notify((int) (System.currentTimeMillis() & 0x7FFFFFF), n);
    }
}
