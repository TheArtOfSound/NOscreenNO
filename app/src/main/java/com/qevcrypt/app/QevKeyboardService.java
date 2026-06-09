package com.qevcrypt.app;

import android.inputmethodservice.InputMethodService;
import android.view.View;
import android.view.inputmethod.InputConnection;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.graphics.Color;
import android.text.InputType;

public class QevKeyboardService extends InputMethodService {
    private EditText seed;
    private EditText plain;
    private TextView status;

    @Override public View onCreateInputView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(12, 10, 12, 12);
        root.setBackgroundColor(Color.rgb(7, 9, 14));

        TextView title = tv("QEV Keyboard", 16, Color.WHITE);
        root.addView(title);

        seed = edit("QEV phrase", true, 1);
        root.addView(seed);
        plain = edit("Type text, then insert encrypted", false, 3);
        root.addView(plain);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button insertEncrypted = btn("Insert encrypted");
        Button decryptSelection = btn("Decrypt selected");
        Button space = btn("Space");
        row.addView(insertEncrypted, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(decryptSelection, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(space, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(row);

        status = tv("Use in any text field. It inserts ciphertext; it cannot rewrite apps that do not expose input.", 12, Color.rgb(188, 196, 204));
        root.addView(status);

        insertEncrypted.setOnClickListener(v -> insertEncrypted());
        decryptSelection.setOnClickListener(v -> decryptSelected());
        space.setOnClickListener(v -> commit(" "));
        return root;
    }

    private void insertEncrypted() {
        try {
            String key = requireSeed();
            String body = plain.getText().toString();
            if (body.trim().isEmpty()) { setStatus("Type something first."); return; }
            String encrypted = QevCrypto.lockText("QEV-KEYBOARD\n---\n" + body, key);
            commit(encrypted);
            plain.setText("");
            setStatus("Encrypted text inserted.");
        } catch (Exception e) { setStatus("Failed: " + clean(e)); }
    }

    private void decryptSelected() {
        try {
            InputConnection ic = getCurrentInputConnection();
            if (ic == null) { setStatus("No active input field."); return; }
            CharSequence selected = ic.getSelectedText(0);
            if (selected == null || selected.toString().trim().isEmpty()) {
                setStatus("Select encrypted qev1 text first.");
                return;
            }
            String unlocked = QevCrypto.unlockText(selected.toString(), requireSeed());
            int idx = unlocked.indexOf("\n---\n");
            if (idx >= 0) unlocked = unlocked.substring(idx + 5);
            ic.commitText(unlocked, 1);
            setStatus("Decrypted text inserted.");
        } catch (Exception e) { setStatus("Decrypt failed: " + clean(e)); }
    }

    private void commit(String s) {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) ic.commitText(s, 1);
    }

    private String requireSeed() {
        String s = seed.getText().toString();
        if (s == null || s.length() < 8) throw new IllegalArgumentException("QEV phrase needs 8+ chars.");
        return s;
    }

    private EditText edit(String hint, boolean password, int lines) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setMinLines(lines);
        e.setTextColor(Color.WHITE);
        e.setHintTextColor(Color.rgb(137, 149, 160));
        e.setInputType(InputType.TYPE_CLASS_TEXT | (password ? InputType.TYPE_TEXT_VARIATION_PASSWORD : InputType.TYPE_TEXT_FLAG_MULTI_LINE));
        return e;
    }

    private TextView tv(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        return t;
    }

    private Button btn(String s) { Button b = new Button(this); b.setText(s); b.setAllCaps(false); return b; }
    private void setStatus(String s) { if (status != null) status.setText(s); }
    private String clean(Exception e) { return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(); }
}
