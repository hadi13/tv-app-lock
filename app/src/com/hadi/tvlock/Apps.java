package com.hadi.tvlock;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;

import java.io.ByteArrayOutputStream;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Installed apps the parent can lock, plus names and icons. */
public final class Apps {
    public static final String[] SETTINGS_PKGS = {"com.android.tv.settings", "com.android.settings"};

    private static Set<String> homes;
    private static long homesAt;
    private static final Map<String, String> labels = new HashMap<String, String>();
    private static final Map<String, byte[]> icons = new HashMap<String, byte[]>();

    private Apps() {}

    public static class Item {
        public final String pkg, label;
        Item(String p, String l) { pkg = p; label = l; }
    }

    public static synchronized Set<String> homePackages(Context c) {
        long now = System.currentTimeMillis();
        if (homes == null || now - homesAt > 60000) {
            Set<String> s = new HashSet<String>();
            for (ResolveInfo ri : c.getPackageManager().queryIntentActivities(
                    new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)) {
                s.add(ri.activityInfo.packageName);
            }
            homes = s;
            homesAt = now;
        }
        return homes;
    }

    public static boolean installed(Context c, String pkg) {
        try { c.getPackageManager().getApplicationInfo(pkg, 0); return true; }
        catch (Exception e) { return false; }
    }

    public static synchronized String label(Context c, String pkg) {
        if (pkg == null) return "";
        String l = labels.get(pkg);
        if (l != null) return l;
        for (String s : SETTINGS_PKGS) if (s.equals(pkg)) { labels.put(pkg, "الإعدادات"); return "الإعدادات"; }
        try {
            PackageManager pm = c.getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            l = pm.getApplicationLabel(ai).toString();
        } catch (Exception e) {
            l = pkg;
        }
        labels.put(pkg, l);
        return l;
    }

    /** App icon as a small PNG for the phone page. */
    public static synchronized byte[] iconPng(Context c, String pkg) {
        byte[] b = icons.get(pkg);
        if (b != null) return b;
        try {
            Drawable d = c.getPackageManager().getApplicationIcon(pkg);
            int sz = 96;
            Bitmap bm = Bitmap.createBitmap(sz, sz, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(bm);
            d.setBounds(0, 0, sz, sz);
            d.draw(cv);
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            bm.compress(Bitmap.CompressFormat.PNG, 100, bo);
            bm.recycle();
            b = bo.toByteArray();
            icons.put(pkg, b);
            return b;
        } catch (Throwable t) {
            return null;
        }
    }

    public static List<Item> list(Context c) {
        PackageManager pm = c.getPackageManager();
        Set<String> seen = new HashSet<String>();
        Set<String> hm = homePackages(c);
        List<ResolveInfo> res = new ArrayList<ResolveInfo>();
        res.addAll(pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0));
        res.addAll(pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0));
        List<Item> out = new ArrayList<Item>();
        for (ResolveInfo ri : res) {
            String p = ri.activityInfo.packageName;
            if (p.equals(c.getPackageName()) || hm.contains(p) || !seen.add(p)) continue;
            out.add(new Item(p, label(c, p)));
        }
        for (String s : SETTINGS_PKGS) {
            if (installed(c, s) && seen.add(s)) out.add(new Item(s, "الإعدادات"));
        }
        final Collator col = Collator.getInstance(new Locale("ar"));
        Collections.sort(out, new Comparator<Item>() {
            @Override public int compare(Item a, Item b) { return col.compare(a.label, b.label); }
        });
        return out;
    }
}
