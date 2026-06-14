package com.qevcrypt.app;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs operations as root via {@code su}. Lets noscreeno reach beyond the app
 * sandbox on a rooted device — read/write/encrypt any path, run privileged
 * commands. Requires the device to actually have root; if not, {@link #isAvailable()}
 * returns false and nothing privileged happens.
 *
 * Supports both common su flavours: Magisk ({@code su -c "cmd"}) on real phones and
 * AOSP/userdebug ({@code su 0 sh -c "cmd"}) on the emulator. All methods block and
 * must be called off the main thread.
 */
public final class RootShell {

    private static final int NONE = 0, MAGISK = 1, AOSP = 2;
    private static int cachedStyle = -1;

    private RootShell() {}

    public static boolean isAvailable() {
        return style() != NONE;
    }

    public static byte[] readFile(String path) throws Exception {
        Process p = run("cat " + q(path));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        drain(p.getInputStream(), out);
        p.waitFor();
        return out.toByteArray();
    }

    public static void writeFile(String path, byte[] data) throws Exception {
        Process p = run("cat > " + q(path));
        try (OutputStream os = p.getOutputStream()) {
            os.write(data);
            os.flush();
        }
        p.waitFor();
    }

    public static List<String> listFiles(String path) throws Exception {
        Process p = run("find " + q(path) + " -type f");
        List<String> files = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) if (!line.trim().isEmpty()) files.add(line.trim());
        }
        p.waitFor();
        return files;
    }

    public static int exec(String cmd) throws Exception {
        Process p = run(cmd);
        drain(p.getInputStream(), new ByteArrayOutputStream());
        return p.waitFor();
    }

    /** Start a root process running the given shell command, using whichever su style works. */
    private static Process run(String cmd) throws Exception {
        return Runtime.getRuntime().exec(suArgs(style(), cmd));
    }

    private static String[] suArgs(int s, String cmd) {
        if (s == AOSP) return new String[]{"su", "0", "sh", "-c", cmd};
        return new String[]{"su", "-c", cmd};   // Magisk (default)
    }

    private static synchronized int style() {
        if (cachedStyle >= 0) return cachedStyle;
        cachedStyle = NONE;
        if (probe(MAGISK)) cachedStyle = MAGISK;
        else if (probe(AOSP)) cachedStyle = AOSP;
        return cachedStyle;
    }

    private static boolean probe(int s) {
        try {
            Process p = Runtime.getRuntime().exec(suArgs(s, "id -u"));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            drain(p.getInputStream(), out);
            int code = p.waitFor();
            return code == 0 && out.toString(StandardCharsets.UTF_8.name()).trim().startsWith("0");
        } catch (Exception e) {
            return false;
        }
    }

    private static String q(String path) {
        return "\"" + path.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static void drain(InputStream in, ByteArrayOutputStream out) throws Exception {
        byte[] buf = new byte[65536];
        int n;
        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
    }
}
