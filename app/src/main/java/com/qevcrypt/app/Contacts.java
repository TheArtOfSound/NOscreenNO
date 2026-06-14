package com.qevcrypt.app;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;

/**
 * Resolves a phone number to a contact display name (READ_CONTACTS). Falls back
 * to the number itself if there's no permission or no match.
 */
public final class Contacts {

    private Contacts() {}

    public static String nameFor(Context c, String number) {
        if (number == null || number.trim().isEmpty()) return number == null ? "" : number;
        if (c.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return number;
        }
        try {
            Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number));
            try (Cursor cur = c.getContentResolver().query(
                    uri, new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME}, null, null, null)) {
                if (cur != null && cur.moveToFirst()) {
                    String name = cur.getString(0);
                    if (name != null && !name.trim().isEmpty()) return name;
                }
            }
        } catch (Exception ignore) {
            // fall through to the number
        }
        return number;
    }
}
