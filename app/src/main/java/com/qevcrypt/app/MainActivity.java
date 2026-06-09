package com.qevcrypt.app;

import android.Manifest;
import android.app.Activity;
import android.app.KeyguardManager;
import android.app.role.RoleManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int PICK_ENCRYPT = 101;
    private static final int PICK_DECRYPT = 102;
    private static final int CREATE_ENCRYPTED = 201;
    private static final int CREATE_DECRYPTED = 202;
    private static final int REQ_NOTIFICATIONS = 301;
    private static final int REQ_DEVICE_UNLOCK = 401;
    private static final int REQ_SMS_ROLE = 402;
    private static final String PREFS = "qev_shield_prefs";
    private static final String KEY_VAULT = "encrypted_vault_note";

    private EditText seed;
    private EditText titleBox;
    private EditText phoneBox;
    private EditText textBox;
    private TextView status;
    private Uri pendingInput;
    private int pendingMode;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Window w = getWindow();
        w.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        buildUi();
        setStatus("Ready. Use this as an encrypted composer, vault, file locker, and launcher shell.");
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(30, 34, 30, 52);
        root.setBackgroundColor(Color.rgb(7, 9, 14));
        scroll.addView(root);

        root.addView(label("QEV Shield", 32, true));
        root.addView(note("Encrypted composer, local vault, file locker, visual shield, and optional home launcher. Local-first. No cloud account required."));

        root.addView(section("1. Unlock / QEV key"));
        seed = input("QEV seed / passphrase — required", true, 1);
        root.addView(seed, full());
        root.addView(note("Use your own phrase for encryption. Device unlock is a convenience gate; the phrase is still the cryptographic key."));

        LinearLayout keyRow = row();
        Button toggleSeed = btn("Show key");
        Button pasteKey = btn("Paste key");
        Button deviceUnlock = btn("Device unlock");
        keyRow.addView(toggleSeed, weight());
        keyRow.addView(pasteKey, weight());
        keyRow.addView(deviceUnlock, weight());
        root.addView(keyRow);
        toggleSeed.setOnClickListener(v -> toggleSeedVisibility(toggleSeed));
        pasteKey.setOnClickListener(v -> seed.setText(readClipboard()));
        deviceUnlock.setOnClickListener(v -> requestDeviceUnlock());

        root.addView(section("2. Encrypted message composer"));
        titleBox = input("Label / thread / client / project", false, 1);
        root.addView(titleBox, full());
        phoneBox = input("Phone number for SMS handoff, optional", false, 1);
        phoneBox.setInputType(InputType.TYPE_CLASS_PHONE);
        root.addView(phoneBox, full());
        textBox = input("Write or paste a message. Encrypt before sending. Paste encrypted text here to decrypt.", false, 10);
        root.addView(textBox, full());

        LinearLayout textRow1 = row();
        Button encryptText = btn("Encrypt");
        Button decryptText = btn("Decrypt");
        Button sendSms = btn("Send SMS");
        textRow1.addView(encryptText, weight());
        textRow1.addView(decryptText, weight());
        textRow1.addView(sendSms, weight());
        root.addView(textRow1);
        encryptText.setOnClickListener(v -> runText(true));
        decryptText.setOnClickListener(v -> runText(false));
        sendSms.setOnClickListener(v -> sendEncryptedSmsIntent());

        LinearLayout textRow2 = row();
        Button copy = btn("Copy");
        Button paste = btn("Paste");
        Button share = btn("Share");
        textRow2.addView(copy, weight());
        textRow2.addView(paste, weight());
        textRow2.addView(share, weight());
        root.addView(textRow2);
        copy.setOnClickListener(v -> copyText(textBox.getText().toString(), "Copied payload."));
        paste.setOnClickListener(v -> { textBox.setText(readClipboard()); setStatus("Pasted clipboard into payload box."); });
        share.setOnClickListener(v -> sharePayload());

        root.addView(section("3. Messaging app mode"));
        root.addView(note("QEV can request the SMS role and can hand encrypted messages to Android SMS. A full default-SMS inbox requires additional protected receiver/service components and carrier/device testing."));
        LinearLayout msgRow = row();
        Button requestSms = btn("Request SMS default");
        Button openSms = btn("Open SMS app");
        msgRow.addView(requestSms, weight());
        msgRow.addView(openSms, weight());
        root.addView(msgRow);
        requestSms.setOnClickListener(v -> requestSmsRole());
        openSms.setOnClickListener(v -> openSystemMessaging());

        root.addView(section("4. Local vault"));
        root.addView(note("Vault stores one encrypted note on-device. SaaS upgrade path: paid sync, team vaults, admin recovery, audit logs, managed keys."));
        LinearLayout vaultRow = row();
        Button saveVault = btn("Save vault");
        Button loadVault = btn("Load vault");
        Button wipeVault = btn("Wipe vault");
        vaultRow.addView(saveVault, weight());
        vaultRow.addView(loadVault, weight());
        vaultRow.addView(wipeVault, weight());
        root.addView(vaultRow);
        saveVault.setOnClickListener(v -> saveVault());
        loadVault.setOnClickListener(v -> loadVault());
        wipeVault.setOnClickListener(v -> wipeVault());

        root.addView(section("5. File locker"));
        root.addView(note("Encrypts/decrypts selected files through Android's file picker. This is the honest non-root way Android permits file privacy."));
        LinearLayout fileRow = row();
        Button encFile = btn("Encrypt file");
        Button decFile = btn("Decrypt .qev");
        fileRow.addView(encFile, weight());
        fileRow.addView(decFile, weight());
        root.addView(fileRow);
        encFile.setOnClickListener(v -> pickFile(PICK_ENCRYPT));
        decFile.setOnClickListener(v -> pickFile(PICK_DECRYPT));

        root.addView(section("6. Home launcher / visual shield"));
        root.addView(note("Set QEV Home as your launcher for a private control surface. The visual shield is touch-through and masks the screen, but does not modify other apps' text."));
        LinearLayout shieldRow = row();
        Button openHome = btn("Open QEV Home");
        Button startOverlay = btn("Start shield");
        Button stopOverlay = btn("Stop shield");
        shieldRow.addView(openHome, weight());
        shieldRow.addView(startOverlay, weight());
        shieldRow.addView(stopOverlay, weight());
        root.addView(shieldRow);
        openHome.setOnClickListener(v -> startActivity(new Intent(this, LauncherActivity.class)));
        startOverlay.setOnClickListener(v -> startShield());
        stopOverlay.setOnClickListener(v -> { stopService(new Intent(this, PrivacyOverlayService.class)); setStatus("Visual shield stopped."); });

        LinearLayout utilityRow = row();
        Button clear = btn("Clear screen");
        Button explain = btn("Limits");
        utilityRow.addView(clear, weight());
        utilityRow.addView(explain, weight());
        root.addView(utilityRow);
        clear.setOnClickListener(v -> { titleBox.setText(""); phoneBox.setText(""); textBox.setText(""); setStatus("Screen cleared."); });
        explain.setOnClickListener(v -> explainLimits());

        status = note("Starting...");
        status.setTextColor(Color.rgb(0, 229, 168));
        root.addView(status);
        setContentView(scroll);
    }

    private void runText(boolean encrypt) {
        try {
            String key = requireSeed();
            String input = textBox.getText().toString();
            if (input.trim().isEmpty()) { setStatus("Nothing to " + (encrypt ? "encrypt" : "decrypt") + "."); return; }
            if (encrypt) {
                String packed = QevCrypto.lockText(makeEnvelope(input), key);
                textBox.setText(packed);
                setStatus("Encrypted. Copy/share it or send through SMS handoff.");
            } else {
                String unlocked = QevCrypto.unlockText(input, key);
                textBox.setText(stripEnvelope(unlocked));
                setStatus("Decrypted successfully.");
            }
        } catch (Exception e) {
            setStatus("Failed: " + cleanError(e));
        }
    }

    private void sendEncryptedSmsIntent() {
        try {
            String key = requireSeed();
            String payload = textBox.getText().toString();
            if (payload.trim().isEmpty()) { setStatus("Write a message first."); return; }
            String encrypted = payload.startsWith("qev1:") ? payload : QevCrypto.lockText(makeEnvelope(payload), key);
            textBox.setText(encrypted);
            String phone = phoneBox.getText().toString().trim();
            Uri uri = Uri.parse(phone.isEmpty() ? "smsto:" : "smsto:" + Uri.encode(phone));
            Intent sms = new Intent(Intent.ACTION_SENDTO, uri);
            sms.putExtra("sms_body", encrypted);
            startActivity(sms);
            setStatus("Encrypted payload handed to Android SMS.");
        } catch (Exception e) {
            setStatus("SMS handoff failed: " + cleanError(e));
        }
    }

    private void requestSmsRole() {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                RoleManager rm = (RoleManager) getSystemService(RoleManager.class);
                if (rm == null || !rm.isRoleAvailable(RoleManager.ROLE_SMS)) { setStatus("SMS role is not available on this device."); return; }
                if (rm.isRoleHeld(RoleManager.ROLE_SMS)) { setStatus("QEV Shield already holds the SMS role."); return; }
                startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_SMS), REQ_SMS_ROLE);
            } else {
                Intent i = new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS);
                startActivity(i);
            }
        } catch (Exception e) {
            setStatus("SMS role request failed. Android may reject this build until full default-SMS receiver components are added.");
        }
    }

    private void openSystemMessaging() {
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_APP_MESSAGING);
        try { startActivity(i); } catch (Exception e) { setStatus("No messaging app shortcut available."); }
    }

    private void requestDeviceUnlock() {
        KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        if (km == null || !km.isDeviceSecure()) { setStatus("Set a device PIN/password/biometric first in Android Settings."); return; }
        Intent i = km.createConfirmDeviceCredentialIntent("Unlock QEV Shield", "Confirm your device credential before working with private messages.");
        if (i == null) { setStatus("Device unlock prompt unavailable."); return; }
        startActivityForResult(i, REQ_DEVICE_UNLOCK);
    }

    private void saveVault() {
        try {
            String key = requireSeed();
            String payload = textBox.getText().toString();
            if (payload.trim().isEmpty()) { setStatus("Vault save needs text first."); return; }
            String encrypted = payload.startsWith("qev1:") ? payload : QevCrypto.lockText(makeEnvelope(payload), key);
            prefs().edit().putString(KEY_VAULT, encrypted).apply();
            textBox.setText(encrypted);
            setStatus("Saved encrypted vault note locally.");
        } catch (Exception e) { setStatus("Vault save failed: " + cleanError(e)); }
    }

    private void loadVault() {
        String encrypted = prefs().getString(KEY_VAULT, null);
        if (encrypted == null || encrypted.isEmpty()) { setStatus("No local vault note exists yet."); return; }
        textBox.setText(encrypted);
        setStatus("Loaded encrypted vault note. Press Decrypt to unlock it.");
    }

    private void wipeVault() {
        prefs().edit().remove(KEY_VAULT).apply();
        setStatus("Local vault wiped.");
    }

    private void sharePayload() {
        String payload = textBox.getText().toString();
        if (payload.trim().isEmpty()) { setStatus("Nothing to share."); return; }
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, titleBox.getText().toString().trim().isEmpty() ? "QEV Shield payload" : titleBox.getText().toString().trim());
        send.putExtra(Intent.EXTRA_TEXT, payload);
        startActivity(Intent.createChooser(send, "Share encrypted payload"));
    }

    private void pickFile(int code) {
        try { requireSeed(); } catch (Exception e) { setStatus(cleanError(e)); return; }
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i, code);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == REQ_DEVICE_UNLOCK) { setStatus(result == RESULT_OK ? "Device unlock confirmed." : "Device unlock cancelled."); return; }
        if (request == REQ_SMS_ROLE) { setStatus(result == RESULT_OK ? "SMS role granted." : "SMS role not granted."); return; }
        if (result != RESULT_OK || data == null) return;
        try {
            if (request == PICK_ENCRYPT || request == PICK_DECRYPT) {
                pendingInput = data.getData();
                pendingMode = request;
                Intent out = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                out.addCategory(Intent.CATEGORY_OPENABLE);
                out.setType("application/octet-stream");
                String ts = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
                out.putExtra(Intent.EXTRA_TITLE, request == PICK_ENCRYPT ? "qev-locked-" + ts + ".qev" : "qev-unlocked-" + ts + ".bin");
                startActivityForResult(out, request == PICK_ENCRYPT ? CREATE_ENCRYPTED : CREATE_DECRYPTED);
            } else if (request == CREATE_ENCRYPTED || request == CREATE_DECRYPTED) {
                Uri outUri = data.getData();
                try (InputStream in = getContentResolver().openInputStream(pendingInput);
                     OutputStream out = getContentResolver().openOutputStream(outUri)) {
                    if (in == null || out == null) throw new IllegalStateException("Android file picker returned an unavailable file stream.");
                    if (pendingMode == PICK_ENCRYPT) QevCrypto.lockStream(in, out, requireSeed());
                    else QevCrypto.unlockStream(in, out, requireSeed());
                }
                setStatus(request == CREATE_ENCRYPTED ? "File encrypted and saved." : "File decrypted and saved.");
            }
        } catch (Exception e) { setStatus("File operation failed: " + cleanError(e)); }
    }

    private void startShield() {
        if (!Settings.canDrawOverlays(this)) {
            setStatus("Grant 'Display over other apps', then return and press Start shield again.");
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
            return;
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
            setStatus("Notification permission requested. After allowing it, press Start shield again.");
            return;
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(new Intent(this, PrivacyOverlayService.class));
            else startService(new Intent(this, PrivacyOverlayService.class));
            setStatus("Visual shield active. It is touch-through; use notification/app to stop it.");
        } catch (Exception e) {
            setStatus("Shield failed: " + cleanError(e));
        }
    }

    private String requireSeed() {
        String key = seed.getText().toString();
        if (key == null || key.length() < 8) throw new IllegalArgumentException("Enter a QEV seed/passphrase of at least 8 characters.");
        return key;
    }

    private String makeEnvelope(String body) {
        String title = titleBox.getText().toString().trim();
        String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
        return "QEV-SHIELD-MESSAGE\nTITLE:" + title + "\nCREATED:" + ts + "\n---\n" + body;
    }

    private String stripEnvelope(String body) {
        int idx = body.indexOf("\n---\n");
        if (body.startsWith("QEV-SHIELD-") && idx >= 0) return body.substring(idx + 5);
        return body;
    }

    private void explainLimits() {
        String msg = "QEV Shield can encrypt selected text/files, act as a private launcher, hand encrypted payloads to SMS, save a local encrypted vault, block screenshots inside QEV, and run a visual privacy mask. A normal Android app cannot rewrite every word inside every other app. True device-wide text replacement needs root, device-owner/MDM, a custom keyboard, or custom ROM-level control.";
        textBox.setText(msg);
        setStatus("Loaded capability limits.");
    }

    private SharedPreferences prefs() { return getSharedPreferences(PREFS, MODE_PRIVATE); }

    private void copyText(String value, String ok) {
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("QEV Shield", value));
        setStatus(ok);
    }

    private String readClipboard() {
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm == null || !cm.hasPrimaryClip() || cm.getPrimaryClip() == null || cm.getPrimaryClip().getItemCount() == 0) return "";
        CharSequence v = cm.getPrimaryClip().getItemAt(0).coerceToText(this);
        return v == null ? "" : v.toString();
    }

    private void toggleSeedVisibility(Button b) {
        int pos = seed.getSelectionStart();
        boolean hidden = (seed.getInputType() & InputType.TYPE_TEXT_VARIATION_PASSWORD) != 0;
        seed.setInputType(InputType.TYPE_CLASS_TEXT | (hidden ? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD : InputType.TYPE_TEXT_VARIATION_PASSWORD));
        seed.setSelection(Math.max(0, pos));
        b.setText(hidden ? "Hide key" : "Show key");
    }

    private String cleanError(Exception e) {
        String m = e.getMessage();
        if (m == null || m.trim().isEmpty()) return e.getClass().getSimpleName();
        if (m.contains("mac check") || m.contains("Tag mismatch")) return "Wrong QEV seed or corrupted payload.";
        return m;
    }

    private EditText input(String hint, boolean password, int minLines) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setTextColor(Color.WHITE);
        e.setHintTextColor(Color.rgb(137, 149, 160));
        e.setMinLines(minLines);
        e.setGravity(minLines > 1 ? Gravity.TOP | Gravity.START : Gravity.CENTER_VERTICAL | Gravity.START);
        e.setInputType(InputType.TYPE_CLASS_TEXT | (password ? InputType.TYPE_TEXT_VARIATION_PASSWORD : InputType.TYPE_TEXT_FLAG_MULTI_LINE));
        return e;
    }

    private TextView label(String s, int sp, boolean bold) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextColor(Color.WHITE);
        v.setTextSize(sp);
        if (bold) v.setTypeface(Typeface.DEFAULT_BOLD);
        return v;
    }

    private TextView section(String s) {
        TextView v = label(s, 21, true);
        v.setPadding(0, 28, 0, 6);
        return v;
    }

    private TextView note(String s) {
        TextView v = label(s, 14, false);
        v.setTextColor(Color.rgb(188, 196, 204));
        v.setPadding(0, 8, 0, 12);
        return v;
    }

    private Button btn(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        return b;
    }

    private LinearLayout row() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        return l;
    }

    private LinearLayout.LayoutParams full() { return new LinearLayout.LayoutParams(-1, -2); }
    private LinearLayout.LayoutParams weight() { return new LinearLayout.LayoutParams(0, -2, 1); }
    private void setStatus(String s) { if (status != null) status.setText("Status: " + s); }
}
