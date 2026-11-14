package com.hadi.tvlock;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Home screen in a tvOS-like style: floating frosted side panel, full-image app tiles that
 * lift on focus with the name shown below. Text and direction follow the device language.
 */
public class HomeActivity extends Activity {
    // Apple dark palette
    static final int LABEL = 0xFFFFFFFF, SECONDARY = 0x99EBEBF5, TERTIARY = 0x4DEBEBF5,
            FILL = 0x33787880, PANEL = 0xB81C1C1E, CARD = 0xFF2C2C2E, BLUE = 0xFF0A84FF,
            ORANGE = 0xFFFF9F0A, RED = 0xFFFF453A, GREEN = 0xFF30D158;
    private static final int COLS = 5;

    private boolean ar, rtl;
    private Locale loc;
    private Typeface light, regular, semi;
    private TextView clock, date, noteTitle, noteText;
    private LinearLayout note, topRow;
    private IconView noteIcon;
    private GridLayout grid;
    private TextView topTitle, allTitle;
    private View homeItem;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable tickClock = new Runnable() {
        @Override public void run() { updateClock(); h.postDelayed(this, 15000); }
    };
    private String lastSignature = "";
    private final Map<String, Integer> tint = new HashMap<String, Integer>();

    private String t(String arabic, String english) { return ar ? arabic : english; }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        loc = Locale.getDefault();
        ar = "ar".equals(loc.getLanguage());
        rtl = TextUtils.getLayoutDirectionFromLocale(loc) == View.LAYOUT_DIRECTION_RTL;
        try {
            light = Typeface.createFromAsset(getAssets(), "fonts/IBMPlexSansArabic-Light.ttf");
            regular = Typeface.createFromAsset(getAssets(), "fonts/IBMPlexSansArabic-Regular.ttf");
            semi = Typeface.createFromAsset(getAssets(), "fonts/IBMPlexSansArabic-SemiBold.ttf");
        } catch (Exception e) {
            light = regular = Typeface.DEFAULT;
            semi = Typeface.DEFAULT_BOLD;
        }

        FrameLayout frame = new FrameLayout(this);
        frame.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF1B2233, 0xFF0D1018, 0xFF05060A}));
        // soft light in a corner, like a blurred wallpaper
        View glow = new View(this);
        GradientDrawable g = new GradientDrawable();
        g.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        g.setColors(new int[]{0x403B5BDB, 0x00000000});
        g.setGradientRadius(dp(420));
        glow.setBackground(g);
        frame.addView(glow, new FrameLayout.LayoutParams(dp(840), dp(840), Gravity.TOP | (rtl ? Gravity.LEFT : Gravity.RIGHT)));
        setContentView(frame);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setLayoutDirection(rtl ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        root.setClipChildren(false);
        root.setClipToPadding(false);
        frame.setClipChildren(false);
        frame.addView(root, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(dp(216), ViewGroup.LayoutParams.MATCH_PARENT);
        pl.setMargins(dp(18), dp(18), dp(4), dp(18));
        root.addView(buildPanel(), pl);

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setVerticalScrollBarEnabled(false);
        sv.setClipChildren(false);
        sv.setClipToPadding(false);
        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        main.setClipChildren(false);
        main.setClipToPadding(false);
        main.setPaddingRelative(dp(30), dp(34), dp(44), dp(40));
        sv.addView(main, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(sv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));

        topTitle = label(t("الأكثر استخداماً", "Most Used"), 19, LABEL, semi);
        main.addView(topTitle);
        topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setClipChildren(false);
        topRow.setClipToPadding(false);
        LinearLayout.LayoutParams trl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        trl.setMargins(0, dp(12), 0, dp(26));
        main.addView(topRow, trl);

        allTitle = label(t("كل التطبيقات", "All Apps"), 19, LABEL, semi);
        main.addView(allTitle);
        grid = new GridLayout(this);
        grid.setColumnCount(COLS);
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        grid.setLayoutDirection(rtl ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gl.setMargins(0, dp(12), 0, 0);
        main.addView(grid, gl);
    }

    // ---------- Floating side panel ----------

    private View buildPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(round(PANEL, 26, 0x14FFFFFF, 1));
        panel.setPadding(dp(18), dp(26), dp(18), dp(18));
        panel.setClipChildren(false);

        clock = label("", 50, LABEL, light);
        clock.setIncludeFontPadding(false);
        panel.addView(clock);
        date = label("", 13, SECONDARY, regular);
        date.setPadding(dp(2), dp(4), 0, 0);
        panel.addView(date);

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.VERTICAL);
        nav.setClipChildren(false);
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nl.setMargins(0, dp(22), 0, 0);
        panel.addView(nav, nl);

        homeItem = navItem(nav, IconView.HOME, t("الرئيسية", "Home"), true, new View.OnClickListener() {
            @Override public void onClick(View v) { focusFirstApp(); }
        });
        navItem(nav, IconView.TV, t("القنوات", "Live TV"), false, new View.OnClickListener() {
            @Override public void onClick(View v) { openLiveTv(); }
        });
        navItem(nav, IconView.LOCK, t("قفل التطبيقات", "App Lock"), false, new View.OnClickListener() {
            @Override public void onClick(View v) { startActivity(new Intent(HomeActivity.this, MainActivity.class)); }
        });
        navItem(nav, IconView.GEAR, t("الإعدادات", "Settings"), false, new View.OnClickListener() {
            @Override public void onClick(View v) {
                try { startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
                catch (Exception ignored) {}
            }
        });

        panel.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1));

        note = new LinearLayout(this);
        note.setOrientation(LinearLayout.VERTICAL);
        note.setPadding(dp(12), dp(11), dp(12), dp(12));
        note.setBackground(round(0x26FFFFFF, 16, 0, 0));
        LinearLayout nh = new LinearLayout(this);
        nh.setGravity(Gravity.CENTER_VERTICAL);
        noteIcon = new IconView(this, IconView.MOON, ORANGE);
        nh.addView(noteIcon, new LinearLayout.LayoutParams(dp(15), dp(15)));
        noteTitle = label("", 12, ORANGE, semi);
        noteTitle.setPaddingRelative(dp(6), 0, 0, 0);
        nh.addView(noteTitle);
        note.addView(nh);
        noteText = label("", 11, SECONDARY, regular);
        noteText.setPadding(0, dp(4), 0, 0);
        note.addView(noteText);
        panel.addView(note, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return panel;
    }

    private View navItem(LinearLayout nav, int icon, String text, final boolean selected, View.OnClickListener l) {
        final LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPaddingRelative(dp(12), dp(9), dp(12), dp(9));
        row.setFocusable(true);
        row.setClickable(true);
        final GradientDrawable bg = round(selected ? FILL : 0x00000000, 12, 0, 0);
        row.setBackground(bg);
        final IconView iv = new IconView(this, icon, selected ? LABEL : SECONDARY);
        row.addView(iv, new LinearLayout.LayoutParams(dp(18), dp(18)));
        final TextView tv = label(text, 15, selected ? LABEL : SECONDARY, selected ? semi : regular);
        tv.setPaddingRelative(dp(11), 0, 0, 0);
        row.addView(tv);
        row.setOnClickListener(l);
        row.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean f) {
                // tvOS focus: white pill, dark content, slight lift
                bg.setColor(f ? LABEL : (selected ? FILL : 0x00000000));
                iv.setColor(f ? 0xFF000000 : (selected ? LABEL : SECONDARY));
                tv.setTextColor(f ? 0xFF000000 : (selected ? LABEL : SECONDARY));
                v.animate().scaleX(f ? 1.04f : 1f).scaleY(f ? 1.04f : 1f).setDuration(140).start();
                v.setElevation(f ? dp(10) : 0);
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(2), 0, dp(2));
        nav.addView(row, lp);
        return row;
    }

    // ---------- Lifecycle ----------

    @Override
    protected void onResume() {
        super.onResume();
        updateClock();
        h.removeCallbacks(tickClock);
        h.postDelayed(tickClock, 15000);
        build();
    }

    @Override
    protected void onPause() {
        super.onPause();
        h.removeCallbacks(tickClock);
    }

    private String timeOf(int minutes) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, minutes / 60);
        c.set(Calendar.MINUTE, minutes % 60);
        return new SimpleDateFormat("h:mm a", loc).format(c.getTime());
    }

    /** Short duration: "45 د" / "45m", "1 س 20 د" / "1h 20m". */
    private String dur(long ms) {
        long min = Math.max(1, (ms + 59999) / 60000);
        long hh = min / 60, mm = min % 60;
        if (ar) {
            if (hh == 0) return String.format(loc, "%d د", mm);
            return mm == 0 ? String.format(loc, "%d س", hh) : String.format(loc, "%d س %d د", hh, mm);
        }
        if (hh == 0) return mm + "m";
        return mm == 0 ? hh + "h" : hh + "h " + mm + "m";
    }

    private void updateClock() {
        Date now = new Date();
        clock.setText(new SimpleDateFormat("h:mm", loc).format(now));
        date.setText(new SimpleDateFormat(ar ? "EEEE، d MMMM" : "EEEE, MMMM d", loc).format(now));

        if (!LockService.isEnabled(this)) {
            note.setVisibility(View.VISIBLE);
            noteIcon.setColor(RED);
            noteTitle.setTextColor(RED);
            noteTitle.setText(t("خدمة القفل مطفية", "App Lock is off"));
            noteText.setText(t("افتح «قفل التطبيقات» وفعّلها", "Open App Lock to turn it on"));
        } else if (Store.bedOn(this)) {
            note.setVisibility(View.VISIBLE);
            noteIcon.setColor(ORANGE);
            noteTitle.setTextColor(ORANGE);
            noteTitle.setText(t("وقت النوم ", "Bedtime ") + timeOf(Store.bedStart(this)));
            if (Rules.inBedtime(this)) {
                noteText.setText(t("التطبيقات مقفلة لين ", "Apps are locked until ") + timeOf(Store.bedEnd(this)));
            } else {
                noteText.setText(t("بعد ", "In ") + dur(Rules.untilBedtime(this))
                        + t("، وبعدها تنقفل التطبيقات", ", then apps lock"));
            }
        } else {
            note.setVisibility(View.GONE);
        }
    }

    // ---------- Apps ----------

    private void build() {
        List<Apps.Item> all = new ArrayList<Apps.Item>();
        Set<String> hidden = Store.hidden(this);
        for (Apps.Item it : Apps.list(this)) if (!hidden.contains(it.pkg)) all.add(it);

        final Map<String, Long> used = new HashMap<String, Long>();
        long now = System.currentTimeMillis();
        for (int d = 0; d < 7; d++) {
            for (Map.Entry<String, Long> e : Usage.forDay(this, Usage.day(now - d * 86400000L)).entrySet()) {
                Long v = used.get(e.getKey());
                used.put(e.getKey(), (v == null ? 0 : v) + e.getValue());
            }
        }
        List<Apps.Item> byUse = new ArrayList<Apps.Item>(all);
        Collections.sort(byUse, new Comparator<Apps.Item>() {
            @Override public int compare(Apps.Item a, Apps.Item b) {
                long ua = used.containsKey(a.pkg) ? used.get(a.pkg) : 0;
                long ub = used.containsKey(b.pkg) ? used.get(b.pkg) : 0;
                return ub > ua ? 1 : ub < ua ? -1 : 0;
            }
        });
        List<Apps.Item> top = new ArrayList<Apps.Item>();
        for (Apps.Item it : byUse) {
            if (top.size() == 3) break;
            Long u = used.get(it.pkg);
            if (u != null && u > 60000) top.add(it);
        }
        final List<Apps.Item> rest = new ArrayList<Apps.Item>(all);
        rest.removeAll(top);

        StringBuilder sig = new StringBuilder();
        for (Apps.Item it : top) sig.append('T').append(it.pkg).append(state(it.pkg)).append('|');
        for (Apps.Item it : rest) sig.append(it.pkg).append(state(it.pkg)).append('|');
        if (sig.toString().equals(lastSignature)) return;
        lastSignature = sig.toString();

        topRow.removeAllViews();
        topTitle.setVisibility(top.isEmpty() ? View.GONE : View.VISIBLE);
        topRow.setVisibility(top.isEmpty() ? View.GONE : View.VISIBLE);
        final List<Apps.Item> topF = top;
        if (!top.isEmpty()) {
            topRow.post(new Runnable() {
                @Override public void run() {
                    int width = topRow.getWidth() > 0 ? topRow.getWidth() : dp(640);
                    int gap = dp(20);
                    int w = (width - gap * 2) / 3;
                    int hgt = w * 9 / 16;
                    for (int i = 0; i < 3; i++) {
                        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT);
                        if (i > 0) lp.setMarginStart(gap);
                        topRow.addView(i < topF.size() ? tile(topF.get(i), w, hgt, true) : new View(HomeActivity.this), lp);
                    }
                }
            });
        }

        grid.removeAllViews();
        grid.post(new Runnable() {
            @Override public void run() {
                int width = grid.getWidth() > 0 ? grid.getWidth() : dp(640);
                int gap = dp(18);
                int cell = (width - gap * (COLS - 1)) / COLS;
                int hgt = cell * 10 / 16;
                for (int i = 0; i < rest.size(); i++) {
                    GridLayout.LayoutParams gl = new GridLayout.LayoutParams();
                    gl.width = cell;
                    int col = i % COLS;
                    gl.setMarginStart(col == 0 ? 0 : gap / 2);
                    gl.setMarginEnd(col == COLS - 1 ? 0 : gap / 2);
                    gl.bottomMargin = dp(6);
                    grid.addView(tile(rest.get(i), cell, hgt, false), gl);
                }
                View f = getCurrentFocus();
                if (f == null || f == homeItem) focusFirstApp();
            }
        });
    }

    private String state(String pkg) {
        boolean locked = Store.isLocked(this, pkg) && !Store.isUnlocked(this, pkg);
        int lim = Store.limit(this, pkg);
        String left = lim > 0 ? String.valueOf(Math.max(0, lim - Usage.today(this, pkg) / 60000)) : "";
        return (locked ? "L" : "") + left;
    }

    private void focusFirstApp() {
        h.postDelayed(new Runnable() {
            @Override public void run() {
                View v = topRow.getVisibility() == View.VISIBLE && topRow.getChildCount() > 0
                        ? topRow.getChildAt(0) : (grid.getChildCount() > 0 ? grid.getChildAt(0) : homeItem);
                if (v != null) v.requestFocus();
            }
        }, 150);
    }

    private Drawable bannerOf(String pkg) {
        PackageManager pm = getPackageManager();
        Drawable d = null;
        try { d = pm.getApplicationBanner(pkg); } catch (Exception ignored) {}
        if (d == null) {
            try {
                Intent li = pm.getLeanbackLaunchIntentForPackage(pkg);
                if (li != null && li.getComponent() != null) d = pm.getActivityBanner(li.getComponent());
            } catch (Exception ignored) {}
        }
        return d;
    }

    private Drawable iconOf(String pkg) {
        try { return getPackageManager().getApplicationIcon(pkg); } catch (Exception e) { return null; }
    }

    /** A dark tint from the icon's own colors, for apps that have no TV banner. */
    private int tintOf(String pkg, Drawable icon) {
        Integer c = tint.get(pkg);
        if (c != null) return c;
        int out = CARD;
        try {
            Bitmap bm = Bitmap.createBitmap(12, 12, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(bm);
            icon.setBounds(0, 0, 12, 12);
            icon.draw(cv);
            long r = 0, g = 0, b = 0, n = 0;
            for (int y = 0; y < 12; y++) for (int x = 0; x < 12; x++) {
                int px = bm.getPixel(x, y);
                if (Color.alpha(px) < 128) continue;
                r += Color.red(px); g += Color.green(px); b += Color.blue(px); n++;
            }
            bm.recycle();
            if (n > 0) {
                float[] hsv = new float[3];
                Color.RGBToHSV((int) (r / n), (int) (g / n), (int) (b / n), hsv);
                hsv[1] = Math.min(hsv[1], 0.55f);
                hsv[2] = 0.30f;
                out = Color.HSVToColor(hsv);
            }
        } catch (Throwable ignored) {}
        tint.put(pkg, out);
        return out;
    }

    /** tvOS-style tile: full image with big rounded corners; lifts on focus and shows its name. */
    private View tile(final Apps.Item it, int w, int hgt, boolean big) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        col.setFocusable(true);
        col.setClickable(true);

        FrameLayout box = new FrameLayout(this);
        Drawable banner = bannerOf(it.pkg);
        Drawable icon = banner == null ? iconOf(it.pkg) : null;
        box.setBackground(round(banner != null ? CARD : (icon != null ? tintOf(it.pkg, icon) : CARD), big ? 20 : 16, 0, 0));
        box.setClipToOutline(true);

        ImageView iv = new ImageView(this);
        if (banner != null) {
            iv.setImageDrawable(banner);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            box.addView(iv, new FrameLayout.LayoutParams(w, hgt));
        } else {
            iv.setImageDrawable(icon);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int sz = (int) (hgt * 0.52f);
            box.addView(iv, new FrameLayout.LayoutParams(sz, sz, Gravity.CENTER));
        }

        // Lock badge (glass circle) on the leading-top corner
        boolean locked = Store.isLocked(this, it.pkg) && !Store.isUnlocked(this, it.pkg);
        if (locked) {
            FrameLayout badge = new FrameLayout(this);
            badge.setBackground(round(0x99000000, 20, 0x33FFFFFF, 1));
            IconView li = new IconView(this, IconView.LOCK, LABEL);
            int bs = dp(big ? 13 : 11);
            badge.addView(li, new FrameLayout.LayoutParams(bs, bs, Gravity.CENTER));
            int d = dp(big ? 26 : 22);
            FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(d, d, Gravity.TOP | Gravity.START);
            bl.setMargins(dp(7), dp(7), dp(7), dp(7));
            box.addView(badge, bl);
        }
        // Time-left pill on the trailing-bottom corner
        int lim = Store.limit(this, it.pkg);
        if (lim > 0) {
            long left = lim * 60000L - Usage.today(this, it.pkg);
            TextView pill = label(left <= 0 ? t("خلص", "Done") : dur(left), big ? 11 : 10, LABEL, semi);
            pill.setBackground(round(left <= 0 ? 0xE6FF453A : 0xB3000000, 20, 0, 0));
            pill.setPadding(dp(7), dp(1), dp(7), dp(2));
            FrameLayout.LayoutParams pl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.END);
            pl.setMargins(dp(7), dp(7), dp(7), dp(7));
            box.addView(pill, pl);
        }
        col.addView(box, new LinearLayout.LayoutParams(w, hgt));

        // Name: always shown for the big cards, only on focus for the grid (as on tvOS)
        final TextView name = label(it.label, big ? 13 : 12, big ? LABEL : SECONDARY, big ? semi : regular);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setGravity(big ? Gravity.START : Gravity.CENTER);
        name.setPadding(dp(2), dp(8), dp(2), 0);
        if (!big) name.setAlpha(0f);
        col.addView(name, new LinearLayout.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT));

        final boolean bigF = big;
        final View boxF = box;
        col.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean f) {
                float zoom = bigF ? 1.07f : 1.12f;
                boxF.animate().scaleX(f ? zoom : 1f).scaleY(f ? zoom : 1f).translationY(f ? -dp(3) : 0)
                        .setDuration(160).start();
                boxF.setElevation(f ? dp(18) : dp(2));
                name.animate().alpha(f || bigF ? 1f : 0f).translationY(f ? dp(4) : 0).setDuration(160).start();
                name.setTextColor(f ? LABEL : (bigF ? LABEL : SECONDARY));
                v.setZ(f ? 10 : 0);
            }
        });
        col.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View x) { launch(it.pkg); }
        });
        box.setElevation(dp(2));
        return col;
    }

    private void openLiveTv() {
        for (Apps.Item it : Apps.list(this)) {
            if (LockService.isLiveTv(it.pkg)) { launch(it.pkg); return; }
        }
    }

    private void launch(String pkg) {
        PackageManager pm = getPackageManager();
        Intent i = null;
        try { i = pm.getLeanbackLaunchIntentForPackage(pkg); } catch (Exception ignored) {}
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        if (i == null) {
            for (String s : Apps.SETTINGS_PKGS) if (s.equals(pkg)) i = new Intent(Settings.ACTION_SETTINGS);
        }
        if (i == null) return;
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { startActivity(i); } catch (Exception ignored) {}
    }

    // ---------- Helpers ----------

    private int dp(float v) { return Ui.dp(this, v); }

    private GradientDrawable round(int fill, float radiusDp, int stroke, float strokeDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        if (stroke != 0) g.setStroke(Math.max(1, dp(strokeDp)), stroke);
        return g;
    }

    private TextView label(String s, float sp, int color, Typeface tf) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(tf);
        t.setTextDirection(View.TEXT_DIRECTION_LOCALE);
        return t;
    }

    @Override
    public void onBackPressed() {
        // Home screen: Back does nothing.
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_HOME) return true;
        return super.onKeyDown(keyCode, event);
    }
}
