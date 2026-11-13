package com.hadi.tvlock;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Settings screen (behind the PIN): choose locked apps, lock everything now, change PIN. */
public class MainActivity extends Activity {
    private static final String[] SETTINGS_PKGS = {"com.android.tv.settings", "com.android.settings"};
    private static final String[] DEFAULT_LOCKED = {"com.android.tv.settings", "com.android.settings", "com.android.vending"};

    private LinearLayout root;
    private Ui.PinPad pad;
    private boolean authed = false;
    private String pendingNewPin = null;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Ui.BG);
        sv.setFillViewport(true);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        int p = Ui.dp(this, 24);
        root.setPadding(p, p, p, p);
        sv.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(sv);
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    @Override
    protected void onStop() {
        super.onStop();
        authed = false; // ask for the PIN again next time
        pendingNewPin = null;
    }

    private void render() {
        root.removeAllViews();
        pad = null;
        if (!Store.hasPin(this)) showSetPin("أهلاً! اختر رقم سري جديد (٤ إلى ٨ أرقام)", false);
        else if (!authed) showAuth();
        else showDashboard();
    }

    // ---------- PIN screens ----------

    private void addPad(String title, Ui.PinPad.Listener l) {
        root.addView(Ui.text(this, "قفل التطبيقات", 34, Ui.ACCENT),
                Ui.lp(this, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 12));
        pad = new Ui.PinPad(this, title);
        pad.setListener(l);
        root.addView(pad);
        pad.post(new Runnable() { @Override public void run() { if (pad != null) pad.focusFirst(); } });
    }

    private void showAuth() {
        addPad("أدخل الرقم السري لفتح الإعدادات", new Ui.PinPad.Listener() {
            @Override public void onPin(String p) {
                if (Store.checkPin(MainActivity.this, p)) { Events.rightPin("الإعدادات"); authed = true; render(); }
                else { pad.error("رقم سري غلط"); Events.wrongPin(MainActivity.this, "الإعدادات", false); }
            }
        });        pad.setAutoCheck(new Ui.PinPad.AutoCheck() {
            @Override public boolean matches(String p) { return Store.checkPin(MainActivity.this, p); }
        });
    }

    private void showSetPin(String title, final boolean changing) {
        pendingNewPin = null;
        addPad(title, new Ui.PinPad.Listener() {
            @Override public void onPin(String p) {
                if (pendingNewPin == null) {
                    pendingNewPin = p;
                    pad.clear();
                    pad.info("أعد كتابة نفس الرقم للتأكيد");
                } else if (pendingNewPin.equals(p)) {
                    Store.setPin(MainActivity.this, p);
                    pendingNewPin = null;
                    if (!Store.isInitialized(MainActivity.this)) {
                        Set<String> def = new HashSet<String>();
                        for (String d : DEFAULT_LOCKED) if (installed(d)) def.add(d);
                        Store.setLockedAll(MainActivity.this, def);
                        Store.setInitialized(MainActivity.this);
                    }
                    authed = true;
                    render();
                } else {
                    pendingNewPin = null;
                    pad.error("الرقمين مو متطابقين، ابدأ من جديد");
                }
            }
        });
    }

    // ---------- Dashboard ----------

    private void showDashboard() {
        root.addView(Ui.text(this, "قفل التطبيقات", 34, Ui.ACCENT),
                Ui.lp(this, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 8));

        boolean on = LockService.isEnabled(this);
        TextView st = Ui.text(this, on
                ? "✅ خدمة القفل شغالة"
                : "⚠️ خدمة القفل مو مفعلة — التطبيقات ما راح تتقفل لين تفعّلها", 20, on ? 0xFF4ADE80 : Ui.DANGER);
        root.addView(st, Ui.lp(this, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 6));

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER);
        Button first = null;
        if (!on) {
            Button en = Ui.button(this, "تفعيل خدمة القفل");
            en.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { openAccessibilitySettings(); }
            });
            actions.addView(en, wrap());
            first = en;
        }
        Button lockAll = Ui.button(this, "🔒 اقفل كل التطبيقات الحين");
        lockAll.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { Store.relockAll(MainActivity.this); render(); }
        });
        actions.addView(lockAll, wrap());
        if (first == null) first = lockAll;

        Button chg = Ui.button(this, "تغيير الرقم السري");
        chg.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                root.removeAllViews();
                showSetPin("اكتب الرقم السري الجديد", true);
            }
        });
        actions.addView(chg, wrap());
        root.addView(actions);

        // Phone viewing
        root.addView(section("📱 شوف الشاشة من جوالك"));
        boolean webOn = Store.webEnabled(this);
        String ip = WebServer.localIp();
        String url = ip == null ? "الشاشة مو متصلة بشبكة" : "http://" + ip + ":" + WebServer.PORT;
        TextView link = Ui.text(this, webOn
                ? "افتح هذا الرابط من جوالك وهو على نفس شبكة البيت:\n" + url
                : "المشاهدة من الجوال مطفية", 20, webOn ? Ui.ACCENT : Ui.MUTED);
        root.addView(link, Ui.lp(this, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 4));
        Button webBtn = Ui.button(this, webOn ? "إيقاف المشاهدة من الجوال" : "تشغيل المشاهدة من الجوال");
        webBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Store.setWebEnabled(MainActivity.this, !Store.webEnabled(MainActivity.this));
                LockService s = LockService.instance;
                if (s != null) s.updateServer();
                render();
            }
        });
        LinearLayout webRow = new LinearLayout(this);
        webRow.setGravity(Gravity.CENTER);
        webRow.addView(webBtn, wrap());
        root.addView(webRow);
        root.addView(Ui.text(this, "من لوحة الجوال تقدر تضبط وقت النوم، والحد اليومي لكل تطبيق، وتشوف سجل الاستخدام والتنبيهات.",
                16, Ui.MUTED), Ui.lp(this, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 4));
        if (Store.bedOn(this)) {
            root.addView(Ui.text(this, (Rules.inBedtime(this) ? "🌙 وقت النوم شغال الحين" : "🌙 وقت النوم")
                    + " من " + Rules.time(Store.bedStart(this)) + " إلى " + Rules.time(Store.bedEnd(this)), 18, 0xFFFBBF24),
                    Ui.lp(this, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 4));
        }

        // Apps that are open right now
        List<AppItem> apps = new ArrayList<AppItem>();
        for (Apps.Item it : Apps.list(this)) apps.add(new AppItem(it.pkg, it.label));
        List<AppItem> open = new ArrayList<AppItem>();
        for (AppItem a : apps) if (Store.isLocked(this, a.pkg) && Store.isUnlocked(this, a.pkg)) open.add(a);
        if (!open.isEmpty()) {
            root.addView(section("مفتوحة حالياً"));
            for (final AppItem a : open) {
                LinearLayout row = new LinearLayout(this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                TextView t = Ui.text(this, "🔓 " + a.label + " — " + remaining(a.pkg), 20, Ui.TEXT);
                row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                Button b = Ui.button(this, "اقفله الحين");
                b.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { Store.relock(MainActivity.this, a.pkg); render(); }
                });
                row.addView(b, wrap());
                root.addView(row, new LinearLayout.LayoutParams(Ui.dp(this, 900), ViewGroup.LayoutParams.WRAP_CONTENT));
            }
        }

        root.addView(section("اختر التطبيقات اللي تبيها مقفلة (اضغط للتبديل)"));
        LinearLayout bulk = new LinearLayout(this);
        bulk.setGravity(Gravity.CENTER);
        final List<AppItem> all = apps;
        Button selAll = Ui.button(this, "اقفل الكل");
        selAll.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Set<String> s = new HashSet<String>();
                for (AppItem a : all) s.add(a.pkg);
                Store.setLockedAll(MainActivity.this, s);
                render();
            }
        });
        Button none = Ui.button(this, "افتح الكل");
        none.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Store.setLockedAll(MainActivity.this, new HashSet<String>());
                Store.relockAll(MainActivity.this);
                render();
            }
        });
        bulk.addView(selAll, wrap());
        bulk.addView(none, wrap());
        root.addView(bulk);

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(3);
        for (final AppItem a : apps) {
            final Button b = Ui.button(this, "");
            b.setTextSize(18);
            setAppText(b, a);
            b.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    boolean now = !Store.isLocked(MainActivity.this, a.pkg);
                    Store.setLocked(MainActivity.this, a.pkg, now);
                    setAppText(b, a);
                }
            });
            GridLayout.LayoutParams gl = new GridLayout.LayoutParams();
            gl.width = Ui.dp(this, 290);
            gl.height = Ui.dp(this, 64);
            int m = Ui.dp(this, 6);
            gl.setMargins(m, m, m, m);
            grid.addView(b, gl);
        }
        root.addView(grid);

        root.addView(Ui.text(this,
                "نصيحة: خلي «الإعدادات» و«متجر Play» مقفلة عشان ما يقدر أحد يطفي الخدمة أو يحذف التطبيق.",
                16, Ui.MUTED), Ui.lp(this, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 14));

        final Button f = first;
        f.post(new Runnable() { @Override public void run() { f.requestFocus(); } });
    }

    private void setAppText(Button b, AppItem a) {
        boolean l = Store.isLocked(this, a.pkg);
        int lim = Store.limit(this, a.pkg);
        b.setText((l ? "🔒 " : "🔓 ") + a.label + (lim > 0 ? "  ⏱" : ""));
        b.setAlpha(l ? 1f : 0.75f);
    }

    private String remaining(String pkg) {
        long u = Store.until(this, pkg);
        if (u == Store.FOREVER) return "مفتوح لين تقفله";
        if (u == Store.ONCE) return "مفتوح هذي المرة بس";
        long min = Math.max(1, (u - System.currentTimeMillis()) / 60000);
        if (min >= 60) return "باقي " + (min / 60) + " ساعة و" + (min % 60) + " دقيقة";
        return "باقي " + min + " دقيقة";
    }

    private TextView section(String s) {
        TextView t = Ui.text(this, s, 22, Ui.TEXT);
        t.setPadding(0, Ui.dp(this, 18), 0, Ui.dp(this, 6));
        return t;
    }

    private LinearLayout.LayoutParams wrap() {
        return Ui.lp(this, ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 64), 8);
    }

    private void openAccessibilitySettings() {
        // Let the settings app open this one time without the lock screen.
        for (String s : SETTINGS_PKGS) if (Store.isLocked(this, s)) Store.unlock(this, s, Store.ONCE);
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
    }

    // ---------- App list ----------

    static class AppItem {
        String pkg, label;
        AppItem(String p, String l) { pkg = p; label = l; }
    }

    private boolean installed(String pkg) {
        try { getPackageManager().getApplicationInfo(pkg, 0); return true; }
        catch (Exception e) { return false; }
    }

    private List<AppItem> loadApps() {
        PackageManager pm = getPackageManager();
        Set<String> seen = new HashSet<String>();
        Set<String> homes = new HashSet<String>();
        for (ResolveInfo ri : pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)) {
            homes.add(ri.activityInfo.packageName);
        }
        List<ResolveInfo> res = new ArrayList<ResolveInfo>();
        res.addAll(pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0));
        res.addAll(pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0));
        List<AppItem> out = new ArrayList<AppItem>();
        for (ResolveInfo ri : res) {
            String p = ri.activityInfo.packageName;
            if (p.equals(getPackageName()) || homes.contains(p) || !seen.add(p)) continue;
            out.add(new AppItem(p, ri.loadLabel(pm).toString()));
        }
        for (String s : SETTINGS_PKGS) {
            if (installed(s) && seen.add(s)) out.add(new AppItem(s, "الإعدادات"));
        }
        final Collator col = Collator.getInstance(new Locale("ar"));
        Collections.sort(out, new Comparator<AppItem>() {
            @Override public int compare(AppItem a, AppItem b) { return col.compare(a.label, b.label); }
        });
        return out;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (pad != null && pad.isAttachedToWindow() && pad.handleKey(keyCode)) return true;
        return super.onKeyDown(keyCode, event);
    }
}
