package com.hadi.tvlock;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

/** Watch time per app per day (milliseconds), kept for 14 days. */
public final class Usage {
    private static final String P = "tvlock_usage";
    private static String cleanedFor = "";

    private Usage() {}

    private static SharedPreferences p(Context c) {
        return c.getApplicationContext().getSharedPreferences(P, Context.MODE_PRIVATE);
    }

    public static String day(long t) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(t);
        return String.format("%04d%02d%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH));
    }

    public static synchronized void add(Context c, String pkg, long ms) {
        String k = day(System.currentTimeMillis()) + "|" + pkg;
        p(c).edit().putLong(k, p(c).getLong(k, 0) + ms).apply();
    }

    public static long today(Context c, String pkg) {
        return p(c).getLong(day(System.currentTimeMillis()) + "|" + pkg, 0);
    }

    /** pkg -> ms for one day (yyyyMMdd). */
    public static Map<String, Long> forDay(Context c, String d) {
        Map<String, Long> m = new HashMap<String, Long>();
        for (Map.Entry<String, ?> e : p(c).getAll().entrySet()) {
            String k = e.getKey();
            if (k.startsWith(d + "|") && e.getValue() instanceof Long) m.put(k.substring(9), (Long) e.getValue());
        }
        return m;
    }

    public static synchronized void cleanup(Context c) {
        String today = day(System.currentTimeMillis());
        if (today.equals(cleanedFor)) return;
        cleanedFor = today;
        String oldest = day(System.currentTimeMillis() - 14L * 86400000L);
        SharedPreferences.Editor e = null;
        for (String k : p(c).getAll().keySet()) {
            if (k.length() > 9 && k.substring(0, 8).compareTo(oldest) < 0) {
                if (e == null) e = p(c).edit();
                e.remove(k);
            }
        }
        if (e != null) e.apply();
    }

    /** "ساعتين و١٠ دقايق" style text. */
    public static String fmt(long ms) {
        long min = ms / 60000;
        if (min < 1) return "أقل من دقيقة";
        long h = min / 60, m = min % 60;
        String hs = h == 1 ? "ساعة" : h == 2 ? "ساعتين" : h + " ساعات";
        String ms2 = m == 1 ? "دقيقة" : m == 2 ? "دقيقتين" : m + " دقيقة";
        if (h == 0) return ms2;
        if (m == 0) return hs;
        return hs + " و" + ms2;
    }
}
