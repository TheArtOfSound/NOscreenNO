package com.qevcrypt.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Window;
import android.view.WindowManager;
import android.widget.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.InputType;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {
    private static final int PICK_ENCRYPT = 101;
    private static final int PICK_DECRYPT = 102;
    private static final int CREATE_ENCRYPTED = 201;
    private static final int CREATE_DECRYPTED = 202;

    private EditText seed;
    private EditText textBox;
    private TextView status;
    private Uri pendingInput;
    private int pendingMode;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Window w = getWindow();
        w.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        buildUi();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 36, 32, 48);
        root.setBackgroundColor(Color.rgb(8,10,15));
        scroll.addView(root);

        root.addView(label("QEV Crypt", 30, true));
        root.addView(note("Local AES-256-GCM encryption. Nothing is uploaded. Your QEV seed/passphrase is never stored."));

        seed = new EditText(this);
        seed.setHint("QEV seed / passphrase");
        seed.setSingleLine(true);
        seed.setTextColor(Color.WHITE);
        seed.setHintTextColor(Color.rgb(145,155,165));
        seed.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        root.addView(seed, full());

        Button toggleSeed = btn("Show / hide QEV seed");
        toggleSeed.setOnClickListener(v -> {
            int pos = seed.getSelectionStart();
            boolean hidden = (seed.getInputType() & InputType.TYPE_TEXT_VARIATION_PASSWORD) != 0;
            seed.setInputType(InputType.TYPE_CLASS_TEXT | (hidden ? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD : InputType.TYPE_TEXT_VARIATION_PASSWORD));
            seed.setSelection(Math.max(0, pos));
        });
        root.addView(toggleSeed);

        textBox = new EditText(this);
        textBox.setHint("Paste notes, keys, JSON, backup text, messages...");
        textBox.setMinLines(8);
        textBox.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        textBox.setTextColor(Color.WHITE);
        textBox.setHintTextColor(Color.rgb(145,155,165));
        textBox.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        root.addView(textBox, full());

        LinearLayout row1 = row();
        Button encryptText = btn("Encrypt text");
        Button decryptText = btn("Decrypt text");
        row1.addView(encryptText, weight());
        row1.addView(decryptText, weight());
        root.addView(row1);
        encryptText.setOnClickListener(v -> runText(true));
        decryptText.setOnClickListener(v -> runText(false));

        LinearLayout row2 = row();
        Button copy = btn("Copy output");
        Button clear = btn("Clear");
        row2.addView(copy, weight());
        row2.addView(clear, weight());
        root.addView(row2);
        copy.setOnClickListener(v -> {
            android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(android.content.ClipData.newPlainText("QEV Crypt", textBox.getText().toString()));
            setStatus("Copied.");
        });
        clear.setOnClickListener(v -> { textBox.setText(""); setStatus("Cleared."); });

        root.addView(section("Files"));
        root.addView(note("Pick a file, then choose where to save the encrypted .qev file or decrypted output. Android protects random app folders, so this uses the system file picker."));
        LinearLayout row3 = row();
        Button encFile = btn("Encrypt file");
        Button decFile = btn("Decrypt .qev");
        row3.addView(encFile, weight());
        row3.addView(decFile, weight());
        root.addView(row3);
        encFile.setOnClickListener(v -> pickFile(PICK_ENCRYPT));
        decFile.setOnClickListener(v -> pickFile(PICK_DECRYPT));

        root.addView(section("Visual Privacy"));
        root.addView(note("Turns on a screen-wide scramble overlay for shoulder-surfing/privacy. It does not read other apps; it visually masks them."));
        LinearLayout row4 = row();
        Button startOverlay = btn("Start shield");
        Button stopOverlay = btn("Stop shield");
        row4.addView(startOverlay, weight());
        row4.addView(stopOverlay, weight());
        root.addView(row4);
        startOverlay.setOnClickListener(v -> startShield());
        stopOverlay.setOnClickListener(v -> { stopService(new Intent(this, PrivacyOverlayService.class)); setStatus("Privacy shield stopped."); });

        status = note("Ready.");
        root.addView(status);
        setContentView(scroll);
    }

    private void runText(boolean encrypt) {
        try {
            String key = seed.getText().toString();
            String input = textBox.getText().toString();
            if (encrypt) textBox.setText(QevCrypto.lockText(input, key));
            else textBox.setText(QevCrypto.unlockText(input, key));
            setStatus(encrypt ? "Text encrypted." : "Text decrypted.");
        } catch (Exception e) { setStatus("Failed: " + e.getMessage()); }
    }

    private void pickFile(int code) {
        if (seed.getText().toString().length() < 8) { setStatus("Enter a QEV seed/passphrase of at least 8 characters first."); return; }
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i, code);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null) return;
        try {
            if (request == PICK_ENCRYPT || request == PICK_DECRYPT) {
                pendingInput = data.getData();
                pendingMode = request;
                Intent out = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                out.addCategory(Intent.CATEGORY_OPENABLE);
                out.setType("application/octet-stream");
                out.putExtra(Intent.EXTRA_TITLE, request == PICK_ENCRYPT ? "encrypted.qev" : "decrypted-output.bin");
                startActivityForResult(out, request == PICK_ENCRYPT ? CREATE_ENCRYPTED : CREATE_DECRYPTED);
            } else if (request == CREATE_ENCRYPTED || request == CREATE_DECRYPTED) {
                Uri outUri = data.getData();
                try (InputStream in = getContentResolver().openInputStream(pendingInput);
                     OutputStream out = getContentResolver().openOutputStream(outUri)) {
                    if (pendingMode == PICK_ENCRYPT) QevCrypto.lockStream(in, out, seed.getText().toString());
                    else QevCrypto.unlockStream(in, out, seed.getText().toString());
                }
                setStatus(request == CREATE_ENCRYPTED ? "File encrypted and saved." : "File decrypted and saved.");
            }
        } catch (Exception e) { setStatus("File operation failed: " + e.getMessage()); }
    }

    private void startShield() {
        if (!Settings.canDrawOverlays(this)) {
            setStatus("Grant Display over other apps, then press Start shield again.");
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
            return;
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) startForegroundService(new Intent(this, PrivacyOverlayService.class));
        else startService(new Intent(this, PrivacyOverlayService.class));
        setStatus("Privacy shield active.");
    }

    private TextView label(String s, int sp, boolean bold) {
        TextView v = new TextView(this); v.setText(s); v.setTextColor(Color.WHITE); v.setTextSize(sp); if (bold) v.setTypeface(Typeface.DEFAULT_BOLD); return v;
    }
    private TextView section(String s) { TextView v = label(s, 22, true); v.setPadding(0, 30, 0, 8); return v; }
    private TextView note(String s) { TextView v = label(s, 14, false); v.setTextColor(Color.rgb(188,196,204)); v.setPadding(0, 8, 0, 14); return v; }
    private Button btn(String s) { Button b = new Button(this); b.setText(s); b.setAllCaps(false); return b; }
    private LinearLayout row() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); return l; }
    private LinearLayout.LayoutParams full() { return new LinearLayout.LayoutParams(-1, -2); }
    private LinearLayout.LayoutParams weight() { return new LinearLayout.LayoutParams(0, -2, 1); }
    private void setStatus(String s) { if (status != null) status.setText(s); }
}
