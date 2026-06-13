package com.qevcrypt.app;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * noscreeno — a Qira product.
 *
 * The whole app sits behind an app-wide lock: you create a key on first run and
 * enter it on every launch. Nothing is shown until you unlock, the key you unlock
 * with is the key everything is encrypted with, and the app re-locks on demand or
 * when it goes to the background.
 */
public class MainActivity extends Activity {

    // ---- palette (Qira) ----
    private static final int BG = 0xFF070B14;
    private static final int SURFACE = 0xFF0D1322;
    private static final int SURFACE2 = 0xFF111A2E;
    private static final int LINE = 0xFF1B2740;
    private static final int LINE2 = 0xFF2A3A5C;
    private static final int TEXT = 0xFFF4F1EA;
    private static final int MUTED = 0xFF9FB0C8;
    private static final int ACCENT = 0xFF34D8F0;
    private static final int ACCENT_PRESS = 0xFF6CF0FF;
    private static final int INK = 0xFF04161C;
    private static final int DANGER = 0xFFE4632A;

    private static final int PICK_ENCRYPT = 101;
    private static final int PICK_DECRYPT = 102;
    private static final int CREATE_ENCRYPTED = 201;
    private static final int CREATE_DECRYPTED = 202;
    private static final int REQ_NOTIFICATIONS = 301;
    private static final String PREFS = "qev_shield_prefs";
    private static final String KEY_VAULT = "encrypted_vault_note";
    private static final String KEY_CHECK = "unlock_check_v1";
    private static final String UNLOCK_TOKEN = "noscreeno-unlock-v1";

    private float density;
    private String masterKey;            // null = locked
    private int currentTab = 0;          // 0 Text, 1 Vault, 2 Files, 3 Shield
    private boolean internalNav;         // true while a child activity (picker/permission/share) is up
    private String shown = "";           // "lock" | "app"

    private ScrollView scroll;
    private LinearLayout root;

    // app views
    private EditText textTitle, textBody, vaultBody;
    private TextView status;
    private LinearLayout[] panels = new LinearLayout[4];
    private TextView[] tabs = new TextView[4];

    // file op state
    private Uri pendingInput;
    private int pendingMode;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        density = getResources().getDisplayMetrics().density;
        scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        scroll.setFillViewport(true);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(root);
        setContentView(scroll);
        // Android 15 (targetSdk 35) is edge-to-edge by default: pad below the status
        // bar and above the nav bar so the header/footer aren't under the system bars.
        scroll.setOnApplyWindowInsetsListener((v, insets) -> {
            int[] tb = barInsets(insets);
            v.setPadding(0, tb[0], 0, tb[1]);
            return insets;
        });
    }

    /** {status-bar top, nav-bar bottom} insets — modern API on R+, deprecated below it. */
    private int[] barInsets(WindowInsets insets) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) return barInsetsR(insets);
        return new int[]{insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetBottom()};
    }

    @android.annotation.TargetApi(Build.VERSION_CODES.R)
    private int[] barInsetsR(WindowInsets insets) {
        android.graphics.Insets b = insets.getInsets(WindowInsets.Type.systemBars());
        return new int[]{b.top, b.bottom};
    }

    @Override protected void onResume() {
        super.onResume();
        internalNav = false;
        renderCurrent();
    }

    @Override protected void onStop() {
        super.onStop();
        // Auto-lock when truly backgrounded (not when our own picker/permission screen is up).
        if (masterKey != null && !internalNav) masterKey = null;
    }

    private void renderCurrent() {
        String want = masterKey == null ? "lock" : "app";
        if (want.equals(shown)) return;   // don't rebuild (preserves typed input)
        shown = want;
        root.removeAllViews();
        if (masterKey == null) buildLock();
        else buildApp();
    }

    private void forceRender() { shown = ""; renderCurrent(); }

    // ---------------------------------------------------------------- lock screen

    private void buildLock() {
        boolean firstRun = prefs().getString(KEY_CHECK, null) == null;
        scroll.setScrollbarFadingEnabled(true);

        LinearLayout pad = new LinearLayout(this);
        pad.setOrientation(LinearLayout.VERTICAL);
        pad.setGravity(Gravity.CENTER_HORIZONTAL);
        pad.setPadding(dp(26), dp(70), dp(26), dp(40));
        root.addView(pad, mw());

        TextView badge = new TextView(this);
        badge.setText("🔒");
        badge.setTextSize(34);
        badge.setGravity(Gravity.CENTER);
        int s = dp(78);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(s, s);
        badge.setLayoutParams(bp);
        badge.setBackground(round(SURFACE2, 22, LINE2, 1));
        badge.setPadding(0, dp(14), 0, 0);
        pad.addView(badge);

        TextView wm = new TextView(this);
        wm.setText("noscreeno");
        wm.setTextColor(ACCENT);
        wm.setTextSize(34);
        wm.setTypeface(Typeface.DEFAULT_BOLD);
        wm.setPadding(0, dp(20), 0, 0);
        pad.addView(wm);

        TextView sub = new TextView(this);
        sub.setText(firstRun
                ? "Create the key that locks this app. There is no reset and no recovery — choose something you'll remember."
                : "Enter your key to unlock. Everything stays hidden until you do.");
        sub.setTextColor(MUTED);
        sub.setTextSize(14);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(dp(6), dp(10), dp(6), dp(22));
        pad.addView(sub, mw());

        final EditText key = input(firstRun ? "Create a key (8+ characters)" : "Your key", true, 1);
        pad.addView(key, mwCard());

        final TextView msg = new TextView(this);
        msg.setTextColor(DANGER);
        msg.setTextSize(13);
        msg.setPadding(dp(4), dp(10), dp(4), 0);
        msg.setGravity(Gravity.CENTER);

        TextView unlock = primaryBtn(firstRun ? "Set key & enter" : "Unlock");
        LinearLayout.LayoutParams up = mwCard();
        up.topMargin = dp(16);
        pad.addView(unlock, up);
        pad.addView(msg, mw());

        Runnable submit = () -> tryUnlock(key.getText().toString(), firstRun, msg);
        unlock.setOnClickListener(v -> submit.run());
        key.setOnEditorActionListener((v, a, e) -> { submit.run(); return true; });

        TextView foot = new TextView(this);
        foot.setText("a Qira product  ·  100% on-device  ·  no account");
        foot.setTextColor(0xFF6C7C97);
        foot.setTextSize(12);
        foot.setGravity(Gravity.CENTER);
        foot.setPadding(0, dp(40), 0, 0);
        pad.addView(foot, mw());
    }

    private void tryUnlock(String key, boolean firstRun, TextView msg) {
        if (key == null || key.length() < 8) { msg.setText("Your key must be at least 8 characters."); return; }
        try {
            if (firstRun) {
                prefs().edit().putString(KEY_CHECK, QevCrypto.lockText(UNLOCK_TOKEN, key)).apply();
                masterKey = key;
            } else {
                String stored = prefs().getString(KEY_CHECK, null);
                if (stored != null && UNLOCK_TOKEN.equals(QevCrypto.unlockText(stored, key))) {
                    masterKey = key;
                } else {
                    msg.setText("Wrong key. Try again.");
                    return;
                }
            }
        } catch (Exception e) {
            msg.setText("Wrong key. Try again.");
            return;
        }
        forceRender();
    }

    private void lock() {
        masterKey = null;
        currentTab = 0;
        forceRender();
    }

    // ---------------------------------------------------------------- main app

    private void buildApp() {
        LinearLayout pad = new LinearLayout(this);
        pad.setOrientation(LinearLayout.VERTICAL);
        pad.setPadding(dp(18), dp(20), dp(18), dp(40));
        root.addView(pad, mw());

        // header
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView wm = new TextView(this);
        wm.setText("noscreeno");
        wm.setTextColor(ACCENT);
        wm.setTextSize(24);
        wm.setTypeface(Typeface.DEFAULT_BOLD);
        header.addView(wm, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView lockBtn = ghostBtn("🔒  Lock");
        lockBtn.setOnClickListener(v -> lock());
        header.addView(lockBtn);
        pad.addView(header, mw());

        TextView unlocked = new TextView(this);
        unlocked.setText("Unlocked. Everything below is encrypted with your key.");
        unlocked.setTextColor(MUTED);
        unlocked.setTextSize(13);
        unlocked.setPadding(0, dp(6), 0, dp(16));
        pad.addView(unlocked, mw());

        // tab bar
        LinearLayout tabbar = new LinearLayout(this);
        tabbar.setOrientation(LinearLayout.HORIZONTAL);
        tabbar.setBackground(round(SURFACE, 13, LINE, 1));
        tabbar.setPadding(dp(4), dp(4), dp(4), dp(4));
        String[] names = {"Text", "Vault", "Files", "Shield"};
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            TextView t = new TextView(this);
            t.setText(names[i]);
            t.setGravity(Gravity.CENTER);
            t.setTextSize(14);
            t.setPadding(0, dp(10), 0, dp(10));
            t.setOnClickListener(v -> selectTab(idx));
            tabs[i] = t;
            tabbar.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        }
        LinearLayout.LayoutParams tbp = mw();
        tbp.bottomMargin = dp(16);
        pad.addView(tabbar, tbp);

        panels[0] = buildTextPanel();
        panels[1] = buildVaultPanel();
        panels[2] = buildFilesPanel();
        panels[3] = buildShieldPanel();
        for (LinearLayout p : panels) pad.addView(p, mw());

        status = new TextView(this);
        status.setTextColor(ACCENT);
        status.setTextSize(13);
        status.setPadding(dp(4), dp(20), dp(4), 0);
        pad.addView(status, mw());

        selectTab(currentTab);
        setStatus("Ready.");
    }

    private void selectTab(int idx) {
        currentTab = idx;
        for (int i = 0; i < 4; i++) {
            boolean on = i == idx;
            if (panels[i] != null) panels[i].setVisibility(on ? View.VISIBLE : View.GONE);
            tabs[i].setBackground(on ? round(ACCENT, 10, ACCENT, 0) : null);
            tabs[i].setTextColor(on ? INK : MUTED);
            tabs[i].setTypeface(on ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        }
    }

    private LinearLayout buildTextPanel() {
        LinearLayout card = card();
        card.addView(cardTitle("Encrypt text"));
        card.addView(note("Paste anything private. Encrypt it into a sealed payload, or decrypt one back."));
        textTitle = input("Label (optional)", false, 1);
        card.addView(textTitle, mwCard());
        textBody = input("Paste text, keys, notes, messages...", false, 8);
        card.addView(textBody, mwCard());
        card.addView(rowOf(primaryBtn("Encrypt", v -> runText(true)), primaryBtn("Decrypt", v -> runText(false))));
        card.addView(rowOf(
                ghostBtn("Copy", v -> copyText(textBody.getText().toString(), "Copied.")),
                ghostBtn("Paste", v -> { textBody.setText(readClipboard()); setStatus("Pasted from clipboard."); }),
                ghostBtn("Share", v -> sharePayload()),
                ghostBtn("Clear", v -> { textTitle.setText(""); textBody.setText(""); setStatus("Cleared."); })));
        return card;
    }

    private LinearLayout buildVaultPanel() {
        LinearLayout card = card();
        card.addView(cardTitle("Local vault"));
        card.addView(note("One encrypted note kept on this device only. It never leaves your phone."));
        vaultBody = input("Type the note you want to keep safe...", false, 8);
        card.addView(vaultBody, mwCard());
        card.addView(rowOf(
                primaryBtn("Save", v -> saveVault()),
                ghostBtn("Load", v -> loadVault()),
                ghostBtn("Wipe", v -> wipeVault())));
        return card;
    }

    private LinearLayout buildFilesPanel() {
        LinearLayout card = card();
        card.addView(cardTitle("File locker"));
        card.addView(note("Encrypt or decrypt files you pick through Android's file picker, into portable .qev files. Encrypted with your key."));
        card.addView(rowOf(
                primaryBtn("Encrypt a file", v -> pickFile(PICK_ENCRYPT)),
                ghostBtn("Decrypt a .qev", v -> pickFile(PICK_DECRYPT))));
        return card;
    }

    private LinearLayout buildShieldPanel() {
        LinearLayout card = card();
        card.addView(cardTitle("Visual shield"));
        card.addView(note("A touch-through privacy mask over the screen for shoulder-surfing and cameras. It makes the screen harder to read; it does not encrypt other apps."));
        card.addView(rowOf(
                primaryBtn("Start shield", v -> startShield()),
                ghostBtn("Stop shield", v -> { stopService(new Intent(this, PrivacyOverlayService.class)); setStatus("Visual shield stopped."); })));
        card.addView(ghostBtn("What this can & can't do", v -> explainLimits()));
        return card;
    }

    // ---------------------------------------------------------------- actions

    private void runText(boolean encrypt) {
        try {
            String input = textBody.getText().toString();
            if (input.trim().isEmpty()) { setStatus("Nothing to " + (encrypt ? "encrypt" : "decrypt") + "."); return; }
            if (encrypt) {
                textBody.setText(QevCrypto.lockText(makeEnvelope(input), masterKey));
                setStatus("Encrypted with your key. Copy, share, or save it.");
            } else {
                textBody.setText(stripEnvelope(QevCrypto.unlockText(input, masterKey)));
                setStatus("Decrypted.");
            }
        } catch (Exception e) { setStatus("Failed: " + cleanError(e)); }
    }

    private void saveVault() {
        try {
            String payload = vaultBody.getText().toString();
            if (payload.trim().isEmpty()) { setStatus("Type a note to save first."); return; }
            String enc = payload.startsWith("qev1:") ? payload : QevCrypto.lockText(makeEnvelope(payload), masterKey);
            prefs().edit().putString(KEY_VAULT, enc).apply();
            vaultBody.setText(enc);
            setStatus("Saved encrypted vault note.");
        } catch (Exception e) { setStatus("Vault save failed: " + cleanError(e)); }
    }

    private void loadVault() {
        String enc = prefs().getString(KEY_VAULT, null);
        if (TextUtils.isEmpty(enc)) { setStatus("No vault note saved yet."); return; }
        try {
            vaultBody.setText(stripEnvelope(QevCrypto.unlockText(enc, masterKey)));
            setStatus("Vault note unlocked.");
        } catch (Exception e) {
            vaultBody.setText(enc);
            setStatus("Loaded (couldn't auto-unlock: " + cleanError(e) + ")");
        }
    }

    private void wipeVault() { prefs().edit().remove(KEY_VAULT).apply(); vaultBody.setText(""); setStatus("Vault wiped."); }

    private void sharePayload() {
        String payload = textBody.getText().toString();
        if (payload.trim().isEmpty()) { setStatus("Nothing to share."); return; }
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        String title = textTitle.getText().toString().trim();
        send.putExtra(Intent.EXTRA_SUBJECT, title.isEmpty() ? "noscreeno payload" : title);
        send.putExtra(Intent.EXTRA_TEXT, payload);
        internalNav = true;
        startActivity(Intent.createChooser(send, "Share encrypted payload"));
    }

    private void pickFile(int code) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        internalNav = true;
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
                String ts = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
                out.putExtra(Intent.EXTRA_TITLE, request == PICK_ENCRYPT ? "qev-locked-" + ts + ".qev" : "qev-unlocked-" + ts + ".bin");
                internalNav = true;
                startActivityForResult(out, request == PICK_ENCRYPT ? CREATE_ENCRYPTED : CREATE_DECRYPTED);
            } else if (request == CREATE_ENCRYPTED || request == CREATE_DECRYPTED) {
                Uri outUri = data.getData();
                try (InputStream in = getContentResolver().openInputStream(pendingInput);
                     OutputStream os = getContentResolver().openOutputStream(outUri)) {
                    if (in == null || os == null) throw new IllegalStateException("File stream unavailable.");
                    if (pendingMode == PICK_ENCRYPT) QevCrypto.lockStream(in, os, masterKey);
                    else QevCrypto.unlockStream(in, os, masterKey);
                }
                setStatus(request == CREATE_ENCRYPTED ? "File encrypted and saved." : "File decrypted and saved.");
            }
        } catch (Exception e) { setStatus("File operation failed: " + cleanError(e)); }
    }

    private void startShield() {
        if (!Settings.canDrawOverlays(this)) {
            setStatus("Grant 'Display over other apps', then press Start shield again.");
            internalNav = true;
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
            return;
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
            setStatus("Allow notifications, then press Start shield again.");
            return;
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(new Intent(this, PrivacyOverlayService.class));
            else startService(new Intent(this, PrivacyOverlayService.class));
            setStatus("Visual shield active. It is touch-through; use the notification to stop it.");
        } catch (Exception e) { setStatus("Shield failed: " + cleanError(e)); }
    }

    private void explainLimits() {
        selectTab(0);
        textBody.setText("noscreeno encrypts the text and files you choose, keeps an encrypted vault note, locks the whole app behind your key, blocks screenshots of its own screen, and runs a visual privacy mask. It cannot encrypt every other app or file on the phone without root or device-owner control — that is an Android security limit, not a missing button.");
        setStatus("Loaded the honest capability note into Text.");
    }

    // ---------------------------------------------------------------- helpers

    private String makeEnvelope(String body) {
        String title = textCurrentTitle();
        String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
        return "QEV-SHIELD-NOTE\nTITLE:" + title + "\nCREATED:" + ts + "\n---\n" + body;
    }

    private String textCurrentTitle() { return textTitle == null ? "" : textTitle.getText().toString().trim(); }

    private String stripEnvelope(String body) {
        int idx = body.indexOf("\n---\n");
        if (body.startsWith("QEV-SHIELD-NOTE") && idx >= 0) return body.substring(idx + 5);
        return body;
    }

    private SharedPreferences prefs() { return getSharedPreferences(PREFS, MODE_PRIVATE); }

    private void copyText(String value, String ok) {
        if (value.trim().isEmpty()) { setStatus("Nothing to copy."); return; }
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("noscreeno", value));
        setStatus(ok);
    }

    private String readClipboard() {
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm == null || !cm.hasPrimaryClip() || cm.getPrimaryClip() == null || cm.getPrimaryClip().getItemCount() == 0) return "";
        CharSequence v = cm.getPrimaryClip().getItemAt(0).coerceToText(this);
        return v == null ? "" : v.toString();
    }

    private String cleanError(Exception e) {
        String m = e.getMessage();
        if (m == null || m.trim().isEmpty()) return e.getClass().getSimpleName();
        if (m.contains("mac check") || m.contains("Tag mismatch")) return "wrong key or corrupted payload";
        return m;
    }

    private void setStatus(String s) { if (status != null) status.setText(s); }

    // ---- view builders ----

    private int dp(float v) { return Math.round(v * density); }

    private LinearLayout.LayoutParams mw() { return new LinearLayout.LayoutParams(-1, -2); }

    private LinearLayout.LayoutParams mwCard() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(10);
        return p;
    }

    private GradientDrawable round(int fill, int radiusDp, int strokeColor, int strokeDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) g.setStroke(dp(strokeDp), strokeColor);
        return g;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(round(SURFACE, 16, LINE, 1));
        c.setPadding(dp(18), dp(18), dp(18), dp(20));
        return c;
    }

    private TextView cardTitle(String s) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextColor(TEXT);
        v.setTextSize(19);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        return v;
    }

    private TextView note(String s) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextColor(MUTED);
        v.setTextSize(13);
        v.setPadding(0, dp(6), 0, dp(6));
        return v;
    }

    private EditText input(String hint, boolean password, int minLines) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setTextColor(TEXT);
        e.setHintTextColor(MUTED);
        e.setTextSize(15);
        e.setMinLines(minLines);
        e.setBackground(round(SURFACE2, 12, LINE2, 1));
        e.setPadding(dp(14), dp(12), dp(14), dp(12));
        e.setGravity(minLines > 1 ? (Gravity.TOP | Gravity.START) : (Gravity.CENTER_VERTICAL | Gravity.START));
        e.setInputType(InputType.TYPE_CLASS_TEXT | (password
                ? InputType.TYPE_TEXT_VARIATION_PASSWORD
                : InputType.TYPE_TEXT_FLAG_MULTI_LINE));
        return e;
    }

    private StateListDrawable btnBg(int normal, int pressed, int radiusDp, int strokeColor, int strokeDp) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed}, round(pressed, radiusDp, strokeColor, strokeDp));
        s.addState(new int[]{}, round(normal, radiusDp, strokeColor, strokeDp));
        return s;
    }

    private TextView baseBtn(String text) {
        TextView b = new TextView(this);
        b.setText(text);
        b.setGravity(Gravity.CENTER);
        b.setTextSize(15);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setPadding(dp(16), dp(13), dp(16), dp(13));
        b.setClickable(true);
        b.setFocusable(true);
        return b;
    }

    private TextView primaryBtn(String text) {
        TextView b = baseBtn(text);
        b.setTextColor(INK);
        b.setBackground(btnBg(ACCENT, ACCENT_PRESS, 11, ACCENT, 0));
        return b;
    }

    private TextView primaryBtn(String text, View.OnClickListener l) { TextView b = primaryBtn(text); b.setOnClickListener(l); return b; }

    private TextView ghostBtn(String text) {
        TextView b = baseBtn(text);
        b.setTextColor(TEXT);
        b.setBackground(btnBg(SURFACE2, LINE, 11, LINE2, 1));
        return b;
    }

    private TextView ghostBtn(String text, View.OnClickListener l) { TextView b = ghostBtn(text); b.setOnClickListener(l); return b; }

    /** A row of equal-weight buttons with spacing. */
    private LinearLayout rowOf(View... views) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rp = mw();
        rp.topMargin = dp(10);
        row.setLayoutParams(rp);
        for (int i = 0; i < views.length; i++) {
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1f);
            if (i > 0) p.leftMargin = dp(10);
            row.addView(views[i], p);
        }
        return row;
    }
}
