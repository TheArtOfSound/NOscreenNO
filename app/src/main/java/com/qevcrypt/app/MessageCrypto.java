package com.qevcrypt.app;

import android.content.SharedPreferences;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;
import java.security.spec.MGF1ParameterSpec;

/**
 * At-rest message encryption that works even while the app is locked.
 *
 * A 2048-bit RSA keypair is generated when the master key is first known. The
 * PUBLIC key is stored in the clear and seals every incoming text immediately
 * (no master key required), so messages that arrive while noscreeno is locked
 * are never written in plaintext. The PRIVATE key is itself encrypted with the
 * master key (via {@link QevCrypto}), so messages can only be opened after the
 * user unlocks. Each message uses a fresh AES-256-GCM key wrapped with RSA-OAEP.
 */
public final class MessageCrypto {

    private static final String PREF_PUB = "msg_pub_v1";
    private static final String PREF_PRIV = "msg_priv_wrapped_v1";
    private static final String RSA_OAEP = "RSA/ECB/OAEPwithSHA-256andMGF1Padding";
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_LEN = 12;
    private static final SecureRandom RNG = new SecureRandom();

    private MessageCrypto() {}

    public static boolean hasKeys(SharedPreferences p) {
        return p.getString(PREF_PUB, null) != null;
    }

    /** Generate the keypair if it doesn't exist yet. Safe to call on every unlock. */
    public static void ensureKeys(SharedPreferences p, String masterKey) throws GeneralSecurityException {
        if (hasKeys(p)) return;
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();
        String pub = Base64.getEncoder().encodeToString(kp.getPublic().getEncoded());     // X.509
        String privB64 = Base64.getEncoder().encodeToString(kp.getPrivate().getEncoded()); // PKCS#8
        String privWrapped = QevCrypto.lockText(privB64, masterKey);
        p.edit().putString(PREF_PUB, pub).putString(PREF_PRIV, privWrapped).apply();
    }

    /** Seal plaintext with the public key. Works while locked. */
    public static String seal(SharedPreferences p, String plaintext) throws GeneralSecurityException {
        String pubB64 = p.getString(PREF_PUB, null);
        if (pubB64 == null) throw new GeneralSecurityException("No message key yet — unlock once to set it up.");
        PublicKey pub = KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(pubB64)));

        byte[] aesKey = new byte[32];
        RNG.nextBytes(aesKey);
        byte[] iv = new byte[IV_LEN];
        RNG.nextBytes(iv);

        Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
        aes.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(aesKey, "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
        byte[] ct = aes.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

        Cipher rsa = Cipher.getInstance(RSA_OAEP);
        rsa.init(Cipher.ENCRYPT_MODE, pub, oaep());
        byte[] wrapped = rsa.doFinal(aesKey);

        Base64.Encoder e = Base64.getEncoder();
        return "m1:" + e.encodeToString(wrapped) + "." + e.encodeToString(iv) + "." + e.encodeToString(ct);
    }

    /** Open a sealed message using the master key (unwraps the private key first). */
    public static String open(SharedPreferences p, String masterKey, String sealed) throws GeneralSecurityException {
        if (sealed == null || !sealed.startsWith("m1:")) throw new GeneralSecurityException("Not a sealed message.");
        String[] parts = sealed.substring(3).split("\\.");
        if (parts.length != 3) throw new GeneralSecurityException("Corrupt sealed message.");
        Base64.Decoder d = Base64.getDecoder();
        byte[] wrapped = d.decode(parts[0]);
        byte[] iv = d.decode(parts[1]);
        byte[] ct = d.decode(parts[2]);

        String privWrapped = p.getString(PREF_PRIV, null);
        if (privWrapped == null) throw new GeneralSecurityException("No message key.");
        byte[] privBytes = d.decode(QevCrypto.unlockText(privWrapped, masterKey));
        PrivateKey priv = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(privBytes));

        Cipher rsa = Cipher.getInstance(RSA_OAEP);
        rsa.init(Cipher.DECRYPT_MODE, priv, oaep());
        byte[] aesKey = rsa.doFinal(wrapped);

        Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
        aes.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aesKey, "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
        return new String(aes.doFinal(ct), StandardCharsets.UTF_8);
    }

    private static OAEPParameterSpec oaep() {
        return new OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);
    }
}
