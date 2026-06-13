package com.qevcrypt.app;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

import org.junit.Test;

/**
 * Pure-JVM tests for the QEV encryption engine. These run on the host JDK via
 * {@code ./gradlew testDebugUnitTest} — no emulator or device required, because
 * QevCrypto depends only on the JCE and java.util.Base64 (API 26+).
 */
public class QevCryptoTest {

    private static final String SEED = "correct-horse-battery-staple";

    @Test
    public void textRoundTrips() throws Exception {
        String secret = "Wallet seed: alpha bravo charlie — 私的なメモ 🔐";
        String locked = QevCrypto.lockText(secret, SEED);

        assertTrue("ciphertext should carry the qev1 marker", locked.startsWith("qev1:"));
        assertNotEquals("ciphertext must not equal plaintext", secret, locked);
        assertEquals("decrypting with the right seed must restore the original", secret,
                QevCrypto.unlockText(locked, SEED));
    }

    @Test
    public void everyEncryptionIsUnique() throws Exception {
        // Random salt + IV per call means identical input never yields identical output.
        String a = QevCrypto.lockText("same input", SEED);
        String b = QevCrypto.lockText("same input", SEED);
        assertNotEquals("salt/IV randomness should make outputs differ", a, b);
    }

    @Test
    public void wrongSeedIsRejected() throws Exception {
        String locked = QevCrypto.lockText("top secret", SEED);
        try {
            QevCrypto.unlockText(locked, "a-completely-different-seed");
            fail("decryption with the wrong seed must throw");
        } catch (GeneralSecurityException expected) {
            // AES-GCM authentication tag mismatch — exactly what we want.
        }
    }

    @Test
    public void tamperedPayloadIsRejected() throws Exception {
        String locked = QevCrypto.lockText("integrity matters", SEED);
        // Flip a character deep in the base64 body to simulate corruption/tampering.
        StringBuilder sb = new StringBuilder(locked);
        int pos = locked.length() - 6;
        char c = sb.charAt(pos);
        sb.setCharAt(pos, c == 'A' ? 'B' : 'A');
        try {
            QevCrypto.unlockText(sb.toString(), SEED);
            fail("a tampered payload must not decrypt");
        } catch (GeneralSecurityException expected) {
            // GCM tag verification failed.
        }
    }

    @Test
    public void shortSeedIsRejected() {
        try {
            QevCrypto.lockText("data", "short");
            fail("seeds under 8 characters must be rejected");
        } catch (GeneralSecurityException expected) {
            assertTrue(expected.getMessage().contains("8 characters"));
        }
    }

    @Test
    public void binaryRoundTrips() throws Exception {
        byte[] data = new byte[4096];
        new SecureRandom().nextBytes(data);
        byte[] packed = QevCrypto.lockBytes(data, SEED);
        assertArrayEquals("byte payloads must round-trip exactly", data,
                QevCrypto.unlockBytes(packed, SEED));
    }

    @Test
    public void nonQevPayloadIsRejected() {
        try {
            QevCrypto.unlockBytes("this is not a qev payload at all".getBytes(StandardCharsets.UTF_8), SEED);
            fail("foreign data must be rejected before any decryption is attempted");
        } catch (GeneralSecurityException expected) {
            assertFalse(expected.getMessage().isEmpty());
        }
    }
}
