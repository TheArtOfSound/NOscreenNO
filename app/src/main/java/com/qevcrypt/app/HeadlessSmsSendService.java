package com.qevcrypt.app;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/**
 * Required for default-SMS eligibility: handles "respond via message" (quick reply
 * from the dialer/lock screen). Minimal for now — present so Android lets noscreeno
 * be the default messaging app.
 */
public class HeadlessSmsSendService extends Service {
    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        stopSelf(startId);
        return START_NOT_STICKY;
    }
}
