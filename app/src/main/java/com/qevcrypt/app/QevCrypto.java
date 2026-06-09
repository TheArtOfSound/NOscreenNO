package com.qevcrypt.app;

import android.util.Base64;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public final class QevCrypto {
    private static final byte[] HEADER = new byte[]{'Q','E','V','C','R','Y','P','T','1'};
    private static final int SALT_BYTES = 16;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int KDF_ROUNDS = 310_000;
    private static final SecureRandom RANDOM = new SecureRandom();

    private QevCrypto() {}

    public static String lockText(String plain, String seed) throws GeneralSecurityException {
        byte[] packed = lockBytes(plain.getBytes(StandardCharsets.UTF_8), seed);
        return "qev1:" + Base64.encodeToString(packed, Base64.NO_WRAP);
    }

    public static String unlockText(String text, String seed) throws GeneralSecurityException {
        String raw = text.trim();
        if (raw.startsWith("qev1:")) raw = raw.substring(5);
        return new String(unlockBytes(Base64.decode(raw, Base64.NO_WRAP), seed), StandardCharsets.UTF_8);
    }

    public static byte[] lockBytes(byte[] plain, String seed) throws GeneralSecurityException {
        byte[] salt = randomBytes(SALT_BYTES);
        byte[] iv = randomBytes(IV_BYTES);
        SecretKey key = keyFromSeed(seed, salt);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
        cipher.updateAAD(HEADER);
        byte[] encrypted = cipher.doFinal(plain);
        ByteBuffer out = ByteBuffer.allocate(HEADER.length + salt.length + iv.length + encrypted.length);
        out.put(HEADER);
        out.put(salt);
        out.put(iv);
        out.put(encrypted);
        return out.array();
    }

    public static byte[] unlockBytes(byte[] packed, String seed) throws GeneralSecurityException {
        int min = HEADER.length + SALT_BYTES + IV_BYTES + 16;
        if (packed.length < min) throw new GeneralSecurityException("QEV payload is too short.");
        ByteBuffer in = ByteBuffer.wrap(packed);
        byte[] actualHeader = new byte[HEADER.length];
        in.get(actualHeader);
        for (int i = 0; i < HEADER.length; i++) {
            if (actualHeader[i] != HEADER[i]) throw new GeneralSecurityException("Not a QEV payload.");
        }
        byte[] salt = new byte[SALT_BYTES];
        byte[] iv = new byte[IV_BYTES];
        in.get(salt);
        in.get(iv);
        byte[] encrypted = new byte[in.remaining()];
        in.get(encrypted);
        SecretKey key = keyFromSeed(seed, salt);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
        cipher.updateAAD(HEADER);
        return cipher.doFinal(encrypted);
    }

    public static void lockStream(InputStream input, OutputStream output, String seed) throws Exception {
        output.write(lockBytes(readAll(input), seed));
    }

    public static void unlockStream(InputStream input, OutputStream output, String seed) throws Exception {
        output.write(unlockBytes(readAll(input), seed));
    }

    private static SecretKey keyFromSeed(String seed, byte[] salt) throws GeneralSecurityException {
        if (seed == null || seed.length() < 8) throw new GeneralSecurityException("QEV seed must be at least 8 characters.");
        PBEKeySpec spec = new PBEKeySpec(("QEV-CRYPT-v1::" + seed).toCharArray(), salt, KDF_ROUNDS, 256);
        byte[] key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        return new SecretKeySpec(key, "AES");
    }

    private static byte[] randomBytes(int len) {
        byte[] out = new byte[len];
        RANDOM.nextBytes(out);
        return out;
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[65536];
        int n;
        while ((n = in.read(buf)) != -1) bos.write(buf, 0, n);
        return bos.toByteArray();
    }
}
