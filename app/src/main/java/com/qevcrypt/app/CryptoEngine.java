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

public final class CryptoEngine {
    private static final byte[] MAGIC = new byte[]{'Q','E','V','C','R','Y','P','T','1'};
    private static final int SALT_LEN = 16;
    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;
    private static final int PBKDF2_ITERATIONS = 310_000;
    private static final SecureRandom RNG = new SecureRandom();

    private CryptoEngine() {}

    public static String encryptText(String plain, String qevSeed) throws GeneralSecurityException {
        byte[] packed = encryptBytes(plain.getBytes(StandardCharsets.UTF_8), qevSeed);
        return "qev1:" + Base64.encodeToString(packed, Base64.NO_WRAP);
    }

    public static String decryptText(String cipherText, String qevSeed) throws GeneralSecurityException {
        String raw = cipherText.trim();
        if (raw.startsWith("qev1:")) raw = raw.substring(5);
        byte[] packed = Base64.decode(raw, Base64.NO_WRAP);
        byte[] plain = decryptBytes(packed, qevSeed);
        return new String(plain, StandardCharsets.UTF_8);
    }

    public static byte[] encryptBytes(byte[] plain, String qevSeed) throws GeneralSecurityException {
        byte[] salt = random(SALT_LEN);
        byte[] iv = random(IV_LEN);
        SecretKey key = deriveKey(qevSeed, salt);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
        cipher.updateAAD(MAGIC);
        byte[] enc = cipher.doFinal(plain);
        ByteBuffer out = ByteBuffer.allocate(MAGIC.length + SALT_LEN + IV_LEN + enc.length);
        out.put(MAGIC);
        out.put(salt);
        out.put(iv);
        out.put(enc);
        return out.array();
    }

    public static byte[] decryptBytes(byte[] packed, String qevSeed) throws GeneralSecurityException {
        if (packed.length < MAGIC.length + SALT_LEN + IV_LEN + 16) throw new GeneralSecurityException("QEV payload is too short.");
        ByteBuffer in = ByteBuffer.wrap(packed);
        byte[] magic = new byte[MAGIC.length];
        in.get(magic);
        for (int i = 0; i < MAGIC.length; i++) {
            if (magic[i] != MAGIC[i]) {
                throw new GeneralSecurityException("Not a QEV Crypt file/text payload.");
            }
        }
        byte[] salt = new byte[SALT_LEN];
        byte[] iv = new byte[IV_LEN];
        in.get(salt);
        in.get(iv);
        byte[] enc = new byte[in.remaining()];
        in.get(enc);
        SecretKey key = deriveKey(qevSeed, salt);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
        cipher.updateAAD(MAGIC);
        return cipher.doFinal(enc);
    }

    public static void encryptStream(InputStream input, OutputStream output, String qevSeed) throws Exception {
        output.write(encryptBytes(readAll(input), qevSeed));
    }

    public static void decryptStream(InputStream input, OutputStream output, String qevSeed) throws Exception {
        output.write(decryptBytes(readAll(input), qevSeed));
    }

    private static SecretKey deriveKey(String qevSeed, byte[] salt) throws GeneralSecurityException {
        if (qevSeed == null || qevSeed.length() < 8) throw new GeneralSecurityException("QEV seed must be at least 8 characters.");
        String material = "QEV-CRYPT-v1::" + qevSeed;
        PBEKeySpec spec = new PBEKeySpec(material.toCharArray(), salt, PBKDF2_ITERATIONS, 256);
        byte[] key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        return new SecretKeySpec(key, "AES");
    }

    private static byte[] random(int len) {
        byte[] out = new byte[len];
        RNG.nextBytes(out);
        return out;
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[64 * 1024];
        int n;
        while ((n = in.read(buf)) != -1) bos.write(buf, 0, n);
        return bos.toByteArray();
    }
}
