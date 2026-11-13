package com.hadi.tvlock;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Full-screen lock: asks for the PIN, then how long to keep the app open. */
public class LockActivity extends Activity {
    public static final String EXTRA_PKG = "pkg";

    /** The app the lock screen is asking about right now, or null. Read by the phone page. */
    static volatile String showingTarget;
    private static LockActivity instance;

    private static int fails = 0;
    private static long blockedUntil = 0;

    private String pkg;
    private LinearLayout root;
    private Ui.PinPad pad;
    private boolean leavingToApp = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Ui.BG);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        int pd = Ui.dp(this, 12);
        root.setPadding(pd, pd, pd, pd);
        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.setFillViewport(true);
        sv.setBackgroundColor(Ui.BG);
        sv.addView(root, new android.widget.ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(sv);
        load(getIntent());
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);
        load(i);
    }

    @Override
    protected void onResume() {
        super.onResume();
        leavingToApp = false;
        instance = this;
        showingTarget = pkg;
        if (pkg != null && Store.isUnlocked(this, pkg)) finish();
    }

    @Override
    protected void onPause() {
        super.onPause();
        showingTarget = null;
    }

    @Override
    protected void onDestroy() {
        if (instance == this) instance = null;
        super.onDestroy();
    }

    /** Called when the parent opened the app from the phone. */
    static void remoteOpened(final String target) {
        final LockActivity a = instance;
        if (a == null) return;
        a.runOnUiThread(new Runnable() {
            @Override public void run() {
                if (target != null && target.equals(a.pkg)) a.openApp();
            }
        });
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (!leavingToApp && !isFinishing()) finish();
    }

    private String label(String p) {
        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(p, 0);
            return pm.getApplicationLabel(ai).toString();
        } catch (Exception e) {
            return p;
        }
    }

    private Drawable icon(String p) {
        try {
            PackageManager pm = getPackageManager();
            Drawable d = pm.getApplicationBanner(p);
            return d != null ? d : pm.getApplicationIcon(p);
        } catch (Exception e) {
            return null;
        }
    }

    private void header() {
        Drawable d = icon(pkg);
        if (d != null) {
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(d);
            iv.setAdjustViewBounds(true);
            iv.setMaxHeight(Ui.dp(this, 64));
            iv.setMaxWidth(Ui.dp(this, 114));
            root.addView(iv, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 64)));
        }
        TextView t = Ui.text(this, "🔒 " + label(pkg) + " مقفل", 24, Ui.TEXT);
        root.addView(t, Ui.lp(this, ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 4));
        String why = Rules.reason(this, pkg);
        if (!why.isEmpty()) {
            root.addView(Ui.text(this, why, 20, 0xFFFBBF24), Ui.lp(this,
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 2));
        }
    }

    private void load(Intent i) {
        pkg = i.getStringExtra(EXTRA_PKG);
        if (pkg == null) { finish(); return; }
        showingTarget = pkg;
        Events.attempt(this, pkg, Rules.reason(this, pkg));
        showPin();
    }

    private void showPin() {
        root.removeAllViews();
        header();
        pad = new Ui.PinPad(this, "أدخل الرقم السري");
        pad.setListener(new Ui.PinPad.Listener() {
            @Override public void onPin(String p) { onPinEntered(p); }
        });
        pad.setAutoCheck(new Ui.PinPad.AutoCheck() {
            @Override public boolean matches(String p) {
                return SystemClock.elapsedRealtime() >= blockedUntil
                        && Store.checkPin(LockActivity.this, p);
            }
        });
        root.addView(pad);
        long wait = blockedUntil - SystemClock.elapsedRealtime();
        if (wait > 0) pad.error("محاولات كثيرة، انتظر " + (wait / 1000 + 1) + " ثانية");
        pad.post(new Runnable() { @Override public void run() { pad.focusFirst(); } });
    }

    private void onPinEntered(String p) {
        long wait = blockedUntil - SystemClock.elapsedRealtime();
        if (wait > 0) {
            pad.error("محاولات كثيرة، انتظر " + (wait / 1000 + 1) + " ثانية");
            return;
        }
        if (Store.checkPin(this, p)) {
            fails = 0;
            Events.rightPin("الشاشة");
            showDurations();
        } else {
            fails++;
            if (fails >= 5) {
                fails = 0;
                blockedUntil = SystemClock.elapsedRealtime() + 60000;
                pad.error("محاولات كثيرة، انتظر دقيقة");
                Events.wrongPin(this, "الشاشة", true);
            } else {
                pad.error("رقم سري غلط");
                Events.wrongPin(this, "الشاشة", false);
            }
        }
    }

    private void showDurations() {
        root.removeAllViews();
        header();
        root.addView(Ui.text(this, "كم تبي التطبيق يظل مفتوح؟", 26, Ui.TEXT),
                Ui.lp(this, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 10));

        LinearLayout row1 = new LinearLayout(this);
        row1.setGravity(Gravity.CENTER);
        LinearLayout row2 = new LinearLayout(this);
        row2.setGravity(Gravity.CENTER);

        Button once = addChoice(row1, "هذي المرة بس\n(يتقفل أول ما تطلع منه)", Store.ONCE);
        addChoice(row1, "ربع ساعة", 15);
        addChoice(row1, "ساعة", 60);
        addChoice(row2, "ساعتين", 120);
        addChoice(row2, "٣ ساعات", 180);
        addChoice(row2, "مفتوح لين أقفله بنفسي", Store.FOREVER);
        Button cancel = Ui.button(this, "إلغاء");
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { goHome(); }
        });
        row2.addView(cancel, Ui.lp(this, ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 72), 8));

        root.addView(row1);
        root.addView(row2);
        once.requestFocus();
    }

    private Button addChoice(LinearLayout row, String label, final long minutesOrMode) {
        Button b = Ui.button(this, label);
        b.setMinWidth(Ui.dp(this, 150));
        b.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                long until = minutesOrMode < 0 ? minutesOrMode
                        : System.currentTimeMillis() + minutesOrMode * 60000L;
                Store.unlock(LockActivity.this, pkg, until);
                openApp();
            }
        });
        row.addView(b, Ui.lp(this, ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 72), 8));
        return b;
    }

    private void openApp() {
        leavingToApp = true;
        PackageManager pm = getPackageManager();
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        finish();
        if (i != null) {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try { startActivity(i); } catch (Exception ignored) {}
        }
    }

    private void goHome() {
        Intent h = new Intent(Intent.ACTION_MAIN);
        h.addCategory(Intent.CATEGORY_HOME);
        h.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(h);
        finish();
    }

    @Override
    public void onBackPressed() { goHome(); }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (pad != null && pad.isAttachedToWindow() && pad.handleKey(keyCode)) return true;
        return super.onKeyDown(keyCode, event);
    }
}
