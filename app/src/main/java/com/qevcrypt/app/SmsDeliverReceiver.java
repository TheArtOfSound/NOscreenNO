package com.qevcrypt.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsMessage;

/**
 * Receives incoming SMS once noscreeno is the default SMS app. The message is
 * stored encrypted (sealed with the public key, so it works even while locked),
 * and a notification is posted so you actually know a text arrived. The store
 * never drops a message, even if the key isn't ready yet.
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

        MessageStore.add(context, MessageStore.IN, from, body.toString());

        try {
            Notifier.newMessage(context, Contacts.nameFor(context, from));
        } catch (Exception ignore) {
            // never let notification failure crash the receiver
        }
    }
}
