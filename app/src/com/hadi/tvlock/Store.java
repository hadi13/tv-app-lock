package com.hadi.tvlock;

import android.content.Context;
import android.content.SharedPreferences;

import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Saved settings: PIN, locked apps, and unlock state. */
public final class Store {
    /** Unlock until I lock it myself. */
    public static final long FOREVER = -1L;
    /** Unlock for this session only, relock when leaving the app. */
    public static final long ONCE = -2L;

    private static final String P = "tvlock";
    private static final String K_PIN = "pin_hash";
    private static final String K_LOCKED = "locked";
    private static final String K_INIT = "initialized";
    private static final String U = "until_";

    private Store() {}

    private static SharedPreferences p(Context c) {
        return c.getApplicationContext().getSharedPreferences(P, Context.MODE_PRIVATE);
    }

    private static String hash(String pin) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(("tvlock#" + pin).getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return pin;
        }
    }

    public static boolean hasPin(Context c) { return p(c).contains(K_PIN); }
    public static void setPin(Context c, String pin) {
        // A new PIN also signs the phone out, so old links stop working.
        p(c).edit().putString(K_PIN, hash(pin)).putString("web_token", newToken()).commit();
    }

    private static String newToken() {
        byte[] b = new byte[24];
        new java.security.SecureRandom().nextBytes(b);
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    public static synchronized String webToken(Context c) {
        String t = p(c).getString("web_token", null);
        if (t == null) { t = newToken(); p(c).edit().putString("web_token", t).commit(); }
        return t;
    }

    public static boolean webEnabled(Context c) { return p(c).getBoolean("web_on", true); }
    public static void setWebEnabled(Context c, boolean on) { p(c).edit().putBoolean("web_on", on).commit(); }
    public static boolean checkPin(Context c, String pin) {
        String h = p(c).getString(K_PIN, null);
        return h != null && h.equals(hash(pin));
    }

    public static boolean isInitialized(Context c) { return p(c).getBoolean(K_INIT, false); }
    public static void setInitialized(Context c) { p(c).edit().putBoolean(K_INIT, true).apply(); }

    public static Set<String> locked(Context c) {
        return new HashSet<String>(p(c).getStringSet(K_LOCKED, new HashSet<String>()));
    }

    public static boolean isLocked(Context c, String pkg) { return locked(c).contains(pkg); }

    public static void setLocked(Context c, String pkg, boolean on) {
        Set<String> s = locked(c);
        if (on) s.add(pkg); else s.remove(pkg);
        p(c).edit().putStringSet(K_LOCKED, s).remove(U + pkg).apply();
    }

    public static void setLockedAll(Context c, Set<String> pkgs) {
        p(c).edit().putStringSet(K_LOCKED, new HashSet<String>(pkgs)).apply();
    }

    /** 0 = locked, FOREVER, ONCE, or an end time in ms. */
    public static long until(Context c, String pkg) { return p(c).getLong(U + pkg, 0L); }

    public static boolean isUnlocked(Context c, String pkg) {
        long u = until(c, pkg);
        if (u == FOREVER || u == ONCE) return true;
        return u > System.currentTimeMillis();
    }

    public static void unlock(Context c, String pkg, long until) {
        p(c).edit().putLong(U + pkg, until).commit();
    }

    public static void relock(Context c, String pkg) {
        p(c).edit().remove(U + pkg).commit();
    }

    public static void relockAll(Context c) {
        SharedPreferences.Editor e = p(c).edit();
        for (String k : p(c).getAll().keySet()) if (k.startsWith(U)) e.remove(k);
        e.commit();
    }

    /** Relock apps opened in ONCE mode, except the one given. */
    public static void relockOnceExcept(Context c, String keep) {
        SharedPreferences.Editor e = null;
        for (Map.Entry<String, ?> en : p(c).getAll().entrySet()) {
            String k = en.getKey();
            if (!k.startsWith(U)) continue;
            Object v = en.getValue();
            if (v instanceof Long && (Long) v == ONCE && !k.equals(U + keep)) {
                if (e == null) e = p(c).edit();
                e.remove(k);
            }
        }
        if (e != null) e.commit();
    }

    // ---------- Apps hidden from the home screen ----------
    public static Set<String> hidden(Context c) {
        return new HashSet<String>(p(c).getStringSet("hidden", new HashSet<String>()));
    }
    public static void setHidden(Context c, String pkg, boolean hide) {
        Set<String> s = hidden(c);
        if (hide) s.add(pkg); else s.remove(pkg);
        p(c).edit().putStringSet("hidden", s).commit();
    }

    // ---------- Daily limit (minutes; 0 = none) ----------
    public static int limit(Context c, String pkg) { return p(c).getInt("limit_" + pkg, 0); }
    public static void setLimit(Context c, String pkg, int min) {
        if (min <= 0) p(c).edit().remove("limit_" + pkg).commit();
        else p(c).edit().putInt("limit_" + pkg, min).commit();
    }

    // ---------- Bedtime (minutes after midnight) ----------
    public static boolean bedOn(Context c) { return p(c).getBoolean("bed_on", false); }
    public static int bedStart(Context c) { return p(c).getInt("bed_start", 21 * 60); }
    public static int bedEnd(Context c) { return p(c).getInt("bed_end", 7 * 60); }
    public static void setBed(Context c, boolean on, int start, int end) {
        p(c).edit().putBoolean("bed_on", on).putInt("bed_start", start).putInt("bed_end", end).commit();
    }
    public static boolean bedApplied(Context c) { return p(c).getBoolean("bed_applied", false); }
    public static void setBedApplied(Context c, boolean v) { p(c).edit().putBoolean("bed_applied", v).commit(); }

    // ---------- Phone push alerts via ntfy ----------
    public static boolean ntfyOn(Context c) { return p(c).getBoolean("ntfy_on", false); }
    public static synchronized String ntfyTopic(Context c) {
        String t = p(c).getString("ntfy_topic", null);
        if (t == null) { t = "tvlock-" + newToken().substring(0, 10); p(c).edit().putString("ntfy_topic", t).commit(); }
        return t;
    }
    public static void setNtfy(Context c, boolean on, String topic) {
        SharedPreferences.Editor e = p(c).edit().putBoolean("ntfy_on", on);
        if (topic != null && topic.matches("[A-Za-z0-9_-]{6,64}")) e.putString("ntfy_topic", topic);
        e.commit();
    }
}
