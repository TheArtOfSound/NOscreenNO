package com.qevcrypt.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Required for default-SMS eligibility. Full MMS handling is out of scope for now;
 * this receiver exists so Android lets noscreeno be the default messaging app.
 */
public class MmsReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        // Intentionally minimal — MMS download/decode is a later increment.
    }
}
