package com.qevcrypt.app;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.SharedPreferences;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Build;
import android.os.CancellationSignal;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyPermanentlyInvalidatedException;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Biometric (fingerprint / face) unlock. The master key is wrapped by an Android
 * Keystore AES key that requires biometric authentication for every use, so the
 * key never exists in plaintext at rest and only decrypts after a successful
 * biometric match. API 29+ (callers must guard with SDK_INT >= 29).
 */
@TargetApi(29)
public final class BiometricGate {

    private static final String KEY = "noscreeno_bio_v1";
    private static final String PREF_IV = "bio_iv_v1";
    private static final String PREF_CT = "bio_ct_v1";

    public interface EnableCallback { void onEnabled(); void onError(String msg); }
    public interface UnlockCallback { void onUnlocked(String masterKey); void onError(String msg); void onFallback(); }

    private BiometricGate() {}

    public static boolean canUse(Activity a) {
        try {
            BiometricManager bm = a.getSystemService(BiometricManager.class);
            return bm != null && bm.canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS;
        } catch (Exception e) { return false; }
    }

    public static boolean isEnabled(SharedPreferences p) {
        return p.getString(PREF_CT, null) != null;
    }

    public static void disable(SharedPreferences p) {
        p.edit().remove(PREF_IV).remove(PREF_CT).apply();
        try { KeyStore ks = loadKeyStore(); ks.deleteEntry(KEY); } catch (Exception ignore) {}
    }

    /** Encrypt the master key behind a biometric-gated keystore key. */
    public static void enable(Activity a, SharedPreferences p, String masterKey, EnableCallback cb) {
        try {
            SecretKey key = getOrCreateKey();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            prompt(a, cipher, "Enable biometric unlock", new BiometricPrompt.AuthenticationCallback() {
                @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                    try {
                        Cipher c = result.getCryptoObject().getCipher();
                        byte[] ct = c.doFinal(masterKey.getBytes(StandardCharsets.UTF_8));
                        p.edit().putString(PREF_IV, Base64.encodeToString(c.getIV(), Base64.NO_WRAP))
                                .putString(PREF_CT, Base64.encodeToString(ct, Base64.NO_WRAP)).apply();
                        cb.onEnabled();
                    } catch (Exception e) { cb.onError(clean(e)); }
                }
                @Override public void onAuthenticationError(int code, CharSequence msg) { cb.onError(String.valueOf(msg)); }
            });
        } catch (Exception e) { cb.onError(clean(e)); }
    }

    /** Biometric prompt → decrypt and return the master key. */
    public static void unlock(Activity a, SharedPreferences p, UnlockCallback cb) {
        try {
            String ivB64 = p.getString(PREF_IV, null), ctB64 = p.getString(PREF_CT, null);
            if (ivB64 == null || ctB64 == null) { cb.onError("Biometric unlock isn't set up."); return; }
            KeyStore ks = loadKeyStore();
            SecretKey key = (SecretKey) ks.getKey(KEY, null);
            if (key == null) { disable(p); cb.onError("Use your passphrase."); return; }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, Base64.decode(ivB64, Base64.NO_WRAP)));
            final byte[] ct = Base64.decode(ctB64, Base64.NO_WRAP);
            prompt(a, cipher, "Unlock noscreeno", new BiometricPrompt.AuthenticationCallback() {
                @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                    try {
                        byte[] mk = result.getCryptoObject().getCipher().doFinal(ct);
                        cb.onUnlocked(new String(mk, StandardCharsets.UTF_8));
                    } catch (Exception e) { cb.onError("Couldn't decrypt — use your passphrase."); }
                }
                @Override public void onAuthenticationError(int code, CharSequence msg) {
                    // Framework error codes: NEGATIVE_BUTTON=10, USER_CANCELED=13, CANCELED=5 — treat as "use passphrase".
                    if (code == 10 || code == 13 || code == 5) cb.onFallback();
                    else cb.onError(String.valueOf(msg));
                }
            });
        } catch (KeyPermanentlyInvalidatedException inval) {
            disable(p);
            cb.onError("Biometrics changed — re-enable with your passphrase.");
        } catch (Exception e) { cb.onError(clean(e)); }
    }

    private static void prompt(Activity a, Cipher cipher, String title, BiometricPrompt.AuthenticationCallback cb) {
        BiometricPrompt bp = new BiometricPrompt.Builder(a)
                .setTitle(title)
                .setSubtitle("noscreeno — on-device")
                .setNegativeButton("Use passphrase", a.getMainExecutor(), (d, w) -> { })
                .build();
        bp.authenticate(new BiometricPrompt.CryptoObject(cipher), new CancellationSignal(), a.getMainExecutor(), cb);
    }

    private static SecretKey getOrCreateKey() throws Exception {
        KeyStore ks = loadKeyStore();
        if (ks.containsAlias(KEY)) {
            SecretKey existing = (SecretKey) ks.getKey(KEY, null);
            if (existing != null) return existing;
        }
        KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        KeyGenParameterSpec.Builder b = new KeyGenParameterSpec.Builder(KEY,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(true)
                .setInvalidatedByBiometricEnrollment(true);
        if (Build.VERSION.SDK_INT >= 30) b.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG);
        else b.setUserAuthenticationValidityDurationSeconds(-1);
        kg.init(b.build());
        return kg.generateKey();
    }

    private static KeyStore loadKeyStore() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        return ks;
    }

    private static String clean(Exception e) {
        String m = e.getMessage();
        return (m == null || m.isEmpty()) ? e.getClass().getSimpleName() : m;
    }
}
