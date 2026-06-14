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
 * Runs operations as root via {@code su}. Present so noscreeno can reach beyond the
 * app sandbox on a rooted device — read/write/encrypt any path, run privileged
 * commands. Every call requires the device to actually have root (Magisk/su); if
 * not, {@link #isAvailable()} returns false and nothing privileged happens.
 *
 * All methods here block and must be called off the main thread.
 */
public final class RootShell {

    private RootShell() {}

    public static boolean isAvailable() {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", "id -u"});
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            drain(p.getInputStream(), out);
            int code = p.waitFor();
            return code == 0 && out.toString(StandardCharsets.UTF_8.name()).trim().startsWith("0");
        } catch (Exception e) {
            return false;
        }
    }

    /** Read a file as root, returning its raw bytes. */
    public static byte[] readFile(String path) throws Exception {
        Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", "cat " + q(path)});
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        drain(p.getInputStream(), out);
        p.waitFor();
        return out.toByteArray();
    }

    /** Write bytes to a file as root (overwrites). */
    public static void writeFile(String path, byte[] data) throws Exception {
        Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", "cat > " + q(path)});
        try (OutputStream os = p.getOutputStream()) {
            os.write(data);
            os.flush();
        }
        p.waitFor();
    }

    /** List every regular file under a path (a single file returns just itself). */
    public static List<String> listFiles(String path) throws Exception {
        Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", "find " + q(path) + " -type f"});
        List<String> files = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) if (!line.trim().isEmpty()) files.add(line.trim());
        }
        p.waitFor();
        return files;
    }

    /** Run an arbitrary command as root. */
    public static int exec(String cmd) throws Exception {
        Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
        return p.waitFor();
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
