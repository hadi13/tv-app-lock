package com.hadi.tvlock;

import android.content.Context;

import java.util.Calendar;

/** Decides whether an app may run right now: PIN lock, bedtime, or daily limit. */
public final class Rules {
    private Rules() {}

    public static boolean lockable(Context c, String pkg) {
        if (pkg == null || pkg.equals(c.getPackageName())) return false;
        if (pkg.equals("android") || pkg.equals("com.android.systemui")) return false;
        return !Apps.homePackages(c).contains(pkg);
    }

    private static int nowMin() {
        Calendar cal = Calendar.getInstance();
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
    }

    public static boolean inBedtime(Context c) {
        if (!Store.bedOn(c)) return false;
        int s = Store.bedStart(c), e = Store.bedEnd(c), m = nowMin();
        if (s == e) return false;
        return s < e ? (m >= s && m < e) : (m >= s || m < e);
    }

    /** Milliseconds until bedtime starts, or -1 if off or already bedtime. */
    public static long untilBedtime(Context c) {
        if (!Store.bedOn(c) || inBedtime(c)) return -1;
        Calendar cal = Calendar.getInstance();
        long now = cal.getTimeInMillis();
        int s = Store.bedStart(c);
        cal.set(Calendar.HOUR_OF_DAY, s / 60);
        cal.set(Calendar.MINUTE, s % 60);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() <= now) cal.add(Calendar.DAY_OF_MONTH, 1);
        return cal.getTimeInMillis() - now;
    }

    public static boolean limitReached(Context c, String pkg) {
        int lim = Store.limit(c, pkg);
        return lim > 0 && Usage.today(c, pkg) >= lim * 60000L;
    }

    /** True when the lock screen must show for this app. A PIN unlock overrides everything. */
    public static boolean blocked(Context c, String pkg) {
        if (!lockable(c, pkg)) return false;
        if (Store.isUnlocked(c, pkg)) return false;
        return Store.isLocked(c, pkg) || inBedtime(c) || limitReached(c, pkg);
    }

    public static String reason(Context c, String pkg) {
        if (inBedtime(c)) return "🌙 وقت النوم من " + time(Store.bedStart(c)) + " إلى " + time(Store.bedEnd(c));
        if (limitReached(c, pkg)) return "⏱ خلص وقت اليوم (" + Usage.fmt(Store.limit(c, pkg) * 60000L) + ")";
        return "";
    }

    /** 21*60 -> "9:00 م". */
    public static String time(int min) {
        int h = min / 60, m = min % 60;
        String ap = h < 12 ? "ص" : "م";
        int h12 = h % 12 == 0 ? 12 : h % 12;
        return h12 + ":" + (m < 10 ? "0" : "") + m + " " + ap;
    }

    /** When the app in use will be locked (ms from now), or -1 if never soon. */
    public static long timeLeft(Context c, String pkg) {
        if (!lockable(c, pkg)) return -1;
        long now = System.currentTimeMillis();
        long best = Long.MAX_VALUE;
        long u = Store.until(c, pkg);
        if (u > now) {
            best = u - now; // timed PIN unlock
        } else if (u != Store.FOREVER && u != Store.ONCE) {
            int lim = Store.limit(c, pkg);
            if (lim > 0) best = Math.min(best, Math.max(0, lim * 60000L - Usage.today(c, pkg)));
        }
        long bed = untilBedtime(c);
        if (bed >= 0) best = Math.min(best, bed);
        return best == Long.MAX_VALUE ? -1 : best;
    }
}
