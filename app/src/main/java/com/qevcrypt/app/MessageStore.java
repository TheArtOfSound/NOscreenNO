package com.qevcrypt.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * On-device, encrypted message log. Each line of {@code messages.dat} is one
 * message. Normally sealed with the RSA public key ("m1:") so it works while
 * locked; if the key isn't ready yet, it's stored in a sandbox-private fallback
 * form ("r0:") so a text is NEVER lost, then upgraded to sealed on the next
 * unlock via {@link #reseal}. Nothing here is readable by other apps.
 */
public final class MessageStore {

    public static final String IN = "in";
    public static final String OUT = "out";

    private static final String FILE = "messages.dat";
    private static final String PREFS = "qev_shield_prefs";
    private static final char SEP = '\u0001';
    private static final Object LOCK = new Object();

    public static final class Msg {
        public final String direction, address, body;
        public final long timestamp;
        Msg(String direction, String address, long timestamp, String body) {
            this.direction = direction; this.address = address; this.timestamp = timestamp; this.body = body;
        }
        public boolean incoming() { return IN.equals(direction); }
    }

    public static final class Conversation {
        public final String address, lastBody;
        public final long lastTime;
        public final boolean lastIncoming;
        public int count;
        Conversation(String address, String lastBody, long lastTime, boolean lastIncoming) {
            this.address = address; this.lastBody = lastBody; this.lastTime = lastTime; this.lastIncoming = lastIncoming;
        }
    }

    private MessageStore() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Group a phone number to a stable conversation key (last 10 digits). */
    public static String normalizeAddr(String a) {
        if (a == null) return "";
        String d = a.replaceAll("[^0-9]", "");
        return d.length() > 10 ? d.substring(d.length() - 10) : d;
    }

    /** Seal and append a message. Never throws on a missing key — falls back so nothing is lost. */
    public static void add(Context c, String direction, String address, String body) {
        long ts = System.currentTimeMillis();
        String record = direction + SEP + (address == null ? "" : address) + SEP + ts + SEP + (body == null ? "" : body);
        String line;
        try {
            line = MessageCrypto.seal(prefs(c), record);
        } catch (Exception noKeyYet) {
            line = "r0:" + Base64.encodeToString(record.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
        }
        appendLine(c, line);
    }

    private static void appendLine(Context c, String line) {
        synchronized (LOCK) {
            try (FileOutputStream fos = c.openFileOutput(FILE, Context.MODE_APPEND)) {
                fos.write((line + "\n").getBytes(StandardCharsets.UTF_8));
            } catch (Exception ignore) {}
        }
    }

    private static Msg parse(Context c, String masterKey, String line) {
        try {
            String rec;
            if (line.startsWith("m1:")) rec = MessageCrypto.open(prefs(c), masterKey, line);
            else if (line.startsWith("r0:")) rec = new String(Base64.decode(line.substring(3), Base64.NO_WRAP), StandardCharsets.UTF_8);
            else return null;
            String[] f = rec.split(String.valueOf(SEP), 4);
            if (f.length == 4) return new Msg(f[0], f[1], parseLong(f[2]), f[3]);
        } catch (Exception ignore) {}
        return null;
    }

    /** Decrypt and return all messages, newest first. Requires the master key. */
    public static List<Msg> list(Context c, String masterKey) {
        List<Msg> out = new ArrayList<>();
        synchronized (LOCK) {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(c.openFileInput(FILE), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;
                    Msg m = parse(c, masterKey, line);
                    if (m != null) out.add(m);
                }
            } catch (java.io.FileNotFoundException none) {
                // no messages yet
            } catch (Exception ignore) {}
        }
        Collections.sort(out, new Comparator<Msg>() {
            public int compare(Msg a, Msg b) { return Long.compare(b.timestamp, a.timestamp); }
        });
        return out;
    }

    /** One entry per conversation (latest message), newest first. */
    public static List<Conversation> conversations(Context c, String masterKey) {
        Map<String, Conversation> map = new LinkedHashMap<>();
        for (Msg m : list(c, masterKey)) {   // already newest-first
            String key = normalizeAddr(m.address);
            Conversation cv = map.get(key);
            if (cv == null) {
                cv = new Conversation(m.address, m.body, m.timestamp, m.incoming());
                map.put(key, cv);
            }
            cv.count++;
        }
        return new ArrayList<>(map.values());
    }

    /** All messages with one address, oldest first (for a thread view). */
    public static List<Msg> thread(Context c, String masterKey, String address) {
        String key = normalizeAddr(address);
        List<Msg> out = new ArrayList<>();
        for (Msg m : list(c, masterKey)) if (normalizeAddr(m.address).equals(key)) out.add(m);
        Collections.reverse(out);   // oldest first
        return out;
    }

    /** Upgrade any plaintext-fallback ("r0:") records to sealed ("m1:") once the key exists. */
    public static void reseal(Context c) {
        if (!MessageCrypto.hasKeys(prefs(c))) return;
        synchronized (LOCK) {
            List<String> lines = new ArrayList<>();
            boolean changed = false;
            try (BufferedReader r = new BufferedReader(new InputStreamReader(c.openFileInput(FILE), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;
                    if (line.startsWith("r0:")) {
                        try {
                            String rec = new String(Base64.decode(line.substring(3), Base64.NO_WRAP), StandardCharsets.UTF_8);
                            lines.add(MessageCrypto.seal(prefs(c), rec));
                            changed = true;
                            continue;
                        } catch (Exception keepRaw) { /* leave as-is */ }
                    }
                    lines.add(line);
                }
            } catch (Exception e) { return; }
            if (changed) {
                try (FileOutputStream fos = c.openFileOutput(FILE, Context.MODE_PRIVATE)) {   // overwrite
                    for (String l : lines) fos.write((l + "\n").getBytes(StandardCharsets.UTF_8));
                } catch (Exception ignore) {}
            }
        }
    }

    private static long parseLong(String s) {
        try { return Long.parseLong(s); } catch (Exception e) { return 0L; }
    }
}
