package com.qevcrypt.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * On-device, encrypted message log. Each line of {@code messages.dat} is one
 * message sealed by {@link MessageCrypto} (public-key, so it works while locked).
 * Nothing here is readable without the master key. noscreeno keeps its own store
 * rather than the system SMS provider, so texts never sit in plaintext where
 * other apps can read them.
 */
public final class MessageStore {

    public static final String IN = "in";
    public static final String OUT = "out";

    private static final String FILE = "messages.dat";
    private static final String PREFS = "qev_shield_prefs";
    private static final char SEP = '';
    private static final Object LOCK = new Object();

    public static final class Msg {
        public final String direction, address, body;
        public final long timestamp;
        Msg(String direction, String address, long timestamp, String body) {
            this.direction = direction; this.address = address; this.timestamp = timestamp; this.body = body;
        }
        public boolean incoming() { return IN.equals(direction); }
    }

    private MessageStore() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Seal and append a message. Uses the public key, so it works while the app is locked. */
    public static void add(Context c, String direction, String address, String body) throws Exception {
        long ts = System.currentTimeMillis();
        String record = direction + SEP + (address == null ? "" : address) + SEP + ts + SEP + (body == null ? "" : body);
        String sealed = MessageCrypto.seal(prefs(c), record);
        synchronized (LOCK) {
            try (FileOutputStream fos = c.openFileOutput(FILE, Context.MODE_APPEND)) {
                fos.write((sealed + "\n").getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    /** Decrypt and return all messages, newest first. Requires the master key. */
    public static List<Msg> list(Context c, String masterKey) {
        List<Msg> out = new ArrayList<>();
        synchronized (LOCK) {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(c.openFileInput(FILE), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.trim().isEmpty()) continue;
                    try {
                        String rec = MessageCrypto.open(prefs(c), masterKey, line.trim());
                        String[] f = rec.split(String.valueOf(SEP), 4);
                        if (f.length == 4) out.add(new Msg(f[0], f[1], parseLong(f[2]), f[3]));
                    } catch (Exception ignoreOne) {
                        // Skip a single unreadable record rather than failing the whole list.
                    }
                }
            } catch (java.io.FileNotFoundException none) {
                // no messages yet
            } catch (Exception e) {
                // unreadable store
            }
        }
        Collections.sort(out, new Comparator<Msg>() {
            public int compare(Msg a, Msg b) { return Long.compare(b.timestamp, a.timestamp); }
        });
        return out;
    }

    public static int count(Context c) {
        synchronized (LOCK) {
            int n = 0;
            try (BufferedReader r = new BufferedReader(new InputStreamReader(c.openFileInput(FILE), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) if (!line.trim().isEmpty()) n++;
            } catch (Exception ignore) {}
            return n;
        }
    }

    private static long parseLong(String s) {
        try { return Long.parseLong(s); } catch (Exception e) { return 0L; }
    }
}
