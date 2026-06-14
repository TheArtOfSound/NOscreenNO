package com.qevcrypt.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsMessage;

/**
 * Receives incoming SMS once noscreeno is the default SMS app. The message is
 * sealed with the public key and appended to the encrypted store immediately —
 * even if the app is locked — so it is never written in plaintext.
 */
public class SmsDeliverReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Telephony.Sms.Intents.SMS_DELIVER_ACTION.equals(intent.getAction())) return;
        SmsMessage[] msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        if (msgs == null || msgs.length == 0) return;

        StringBuilder body = new StringBuilder();
        String from = null;
        for (SmsMessage m : msgs) {
            if (m == null) continue;
            if (from == null) from = m.getDisplayOriginatingAddress();
            String part = m.getMessageBody();
            if (part != null) body.append(part);
        }
        try {
            MessageStore.add(context, MessageStore.IN, from, body.toString());
        } catch (Exception e) {
            // A broadcast receiver must never crash; if sealing fails (e.g. no key yet),
            // the message is simply not stored.
        }
    }
}
