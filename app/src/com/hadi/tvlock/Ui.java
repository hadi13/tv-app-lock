package com.hadi.tvlock;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** UI helpers sized for a TV remote. */
public final class Ui {
    public static final int BG = 0xFF0F172A;
    public static final int CARD = 0xFF1E293B;
    public static final int ACCENT = 0xFF38BDF8;
    public static final int TEXT = 0xFFF1F5F9;
    public static final int MUTED = 0xFF94A3B8;
    public static final int DANGER = 0xFFF87171;

    private Ui() {}

    public static int dp(Context c, float v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    public static TextView text(Context c, String s, float sp, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    private static GradientDrawable round(Context c, int fill, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(c, 12));
        if (stroke != 0) g.setStroke(dp(c, 3), stroke);
        return g;
    }

    /** A button with a clear highlight when focused by the remote. */
    public static Button button(Context c, String s) {
        Button b = new Button(c);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(20);
        b.setTextColor(new ColorStateList(
                new int[][]{{android.R.attr.state_focused}, {}},
                new int[]{0xFF0F172A, TEXT}));
        StateListDrawable sl = new StateListDrawable();
        sl.addState(new int[]{android.R.attr.state_focused}, round(c, ACCENT, Color.WHITE));
        sl.addState(new int[]{android.R.attr.state_pressed}, round(c, ACCENT, 0));
        sl.addState(new int[]{}, round(c, CARD, 0));
        b.setBackground(sl);
        b.setFocusable(true);
        b.setPadding(dp(c, 18), dp(c, 10), dp(c, 18), dp(c, 10));
        b.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean f) {
                v.animate().scaleX(f ? 1.06f : 1f).scaleY(f ? 1.06f : 1f).setDuration(120).start();
            }
        });
        return b;
    }

    public static LinearLayout.LayoutParams lp(Context c, int w, int h, int margin) {
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(w, h);
        int m = dp(c, margin);
        l.setMargins(m, m, m, m);
        return l;
    }

    /** Number pad that works with the remote's arrows and digit keys. */
    public static class PinPad extends LinearLayout {
        public interface Listener { void onPin(String pin); }
        /** Returns true when the typed PIN is already correct, so it submits without pressing ✓. */
        public interface AutoCheck { boolean matches(String pin); }

        private final StringBuilder pin = new StringBuilder();
        private final TextView dots;
        private final TextView msg;
        private Listener listener;
        private AutoCheck autoCheck;
        private Button first;

        public PinPad(Context c, String title) {
            super(c);
            // Side by side: title and field on one side, number pad on the other, to fit TV height.
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER);
            LinearLayout info = new LinearLayout(c);
            info.setOrientation(VERTICAL);
            info.setGravity(Gravity.CENTER);
            TextView t = text(c, title, 24, TEXT);
            t.setMaxWidth(dp(c, 340));
            info.addView(t, lp(c, LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, 6));
            dots = text(c, "", 32, ACCENT);
            dots.setMinWidth(dp(c, 240));
            dots.setMinHeight(dp(c, 52));
            dots.setBackground(round(c, CARD, 0));
            dots.setPadding(dp(c, 16), dp(c, 4), dp(c, 16), dp(c, 4));
            info.addView(dots, lp(c, LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, 8));
            msg = text(c, " ", 17, DANGER);
            msg.setMaxWidth(dp(c, 340));
            info.addView(msg, lp(c, LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, 2));
            addView(info, lp(c, LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, 16));

            GridLayout g = new GridLayout(c);
            g.setColumnCount(3);
            g.setLayoutDirection(LAYOUT_DIRECTION_LTR);
            String[] keys = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "⌫", "0", "✓"};
            for (final String k : keys) {
                Button b = button(c, k);
                b.setTextSize(22);
                b.setPadding(0, 0, 0, 0);
                GridLayout.LayoutParams gl = new GridLayout.LayoutParams();
                gl.width = dp(c, 84);
                gl.height = dp(c, 56);
                int m = dp(c, 5);
                gl.setMargins(m, m, m, m);
                b.setOnClickListener(new OnClickListener() {
                    @Override public void onClick(View v) { press(k); }
                });
                if (k.equals("1")) first = b;
                g.addView(b, gl);
            }
            addView(g, lp(c, LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, 16));
        }

        public void setListener(Listener l) { listener = l; }

        public void setAutoCheck(AutoCheck a) { autoCheck = a; }

        public void focusFirst() { if (first != null) first.requestFocus(); }

        public void clear() { pin.setLength(0); render(); }

        public void error(String m) { msg.setText(m); clear(); }

        public void info(String m) { msg.setTextColor(MUTED); msg.setText(m); }

        private void render() {
            StringBuilder s = new StringBuilder();
            for (int i = 0; i < pin.length(); i++) s.append("● ");
            dots.setText(s.toString().trim());
        }

        void press(String k) {
            msg.setTextColor(DANGER);
            if (k.equals("⌫")) {
                if (pin.length() > 0) pin.setLength(pin.length() - 1);
            } else if (k.equals("✓")) {
                if (pin.length() < 4) { msg.setText("الرقم السري ٤ أرقام على الأقل"); return; }
                String p = pin.toString();
                if (listener != null) listener.onPin(p);
                return;
            } else if (pin.length() < 8) {
                pin.append(k);
                msg.setText(" ");
                render();
                if (pin.length() >= 4 && autoCheck != null && listener != null
                        && autoCheck.matches(pin.toString())) {
                    listener.onPin(pin.toString());
                }
                return;
            }
            render();
        }

        /** Handles the remote's digit keys; returns true if used. */
        public boolean handleKey(int keyCode) {
            if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
                press(String.valueOf(keyCode - KeyEvent.KEYCODE_0));
                return true;
            }
            if (keyCode >= KeyEvent.KEYCODE_NUMPAD_0 && keyCode <= KeyEvent.KEYCODE_NUMPAD_9) {
                press(String.valueOf(keyCode - KeyEvent.KEYCODE_NUMPAD_0));
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DEL) { press("⌫"); return true; }
            return false;
        }
    }
}
