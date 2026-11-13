package com.hadi.tvlock;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/** Alert log shown on the phone page, optionally pushed to the phone through ntfy.sh. */
public final class Events {
    private static final String P = "tvlock_events";
    private static final Map<String, Integer> wrong = new HashMap<String, Integer>();
    private static final Map<String, Long> lastAttempt = new HashMap<String, Long>();

    private Events() {}

    private static SharedPreferences p(Context c) {
        return c.getApplicationContext().getSharedPreferences(P, Context.MODE_PRIVATE);
    }

    public static synchronized JSONArray list(Context c) {
        try { return new JSONArray(p(c).getString("list", "[]")); }
        catch (Exception e) { return new JSONArray(); }
    }

    public static long lastId(Context c) { return p(c).getLong("next", 1) - 1; }

    public static synchronized void add(Context c, String icon, String text, boolean push) {
        try {
            long id = p(c).getLong("next", 1);
            JSONArray old = list(c);
            JSONArray a = new JSONArray();
            JSONObject o = new JSONObject();
            o.put("id", id);
            o.put("ts", System.currentTimeMillis());
            o.put("icon", icon);
            o.put("text", text);
            a.put(o);
            for (int i = 0; i < old.length() && i < 79; i++) a.put(old.get(i));
            p(c).edit().putString("list", a.toString()).putLong("next", id + 1).commit();
        } catch (Exception ignored) {}
        if (push) push(c, icon + " " + text);
    }

    /** Sends a push notification through ntfy.sh, if enabled. */
    public static void push(final Context c, final String text) {
        if (!Store.ntfyOn(c)) return;
        final String topic = Store.ntfyTopic(c);
        new Thread(new Runnable() {
            @Override public void run() {
                HttpURLConnection h = null;
                try {
                    JSONObject o = new JSONObject();
                    o.put("topic", topic);
                    o.put("title", "شاشة التلفزيون");
                    o.put("message", text);
                    o.put("priority", 4);
                    h = (HttpURLConnection) new URL("https://ntfy.sh/").openConnection();
                    h.setConnectTimeout(8000);
                    h.setReadTimeout(8000);
                    h.setRequestMethod("POST");
                    h.setDoOutput(true);
                    h.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                    OutputStream os = h.getOutputStream();
                    os.write(o.toString().getBytes("UTF-8"));
                    os.close();
                    h.getResponseCode();
                } catch (Exception ignored) {
                } finally {
                    if (h != null) h.disconnect();
                }
            }
        }).start();
    }

    /** A wrong PIN; alerts on the 2nd in a row and on lockout. */
    public static synchronized void wrongPin(Context c, String where, boolean lockedOut) {
        Integer n = wrong.get(where);
        n = n == null ? 1 : n + 1;
        wrong.put(where, n);
        if (lockedOut) add(c, "🚫", "٥ محاولات رقم سري غلط على " + where + "، الإدخال توقف دقيقة", true);
        else if (n == 2) add(c, "⚠️", "أحد كتب الرقم السري غلط مرتين على " + where, true);
    }

    public static synchronized void rightPin(String where) { wrong.remove(where); }

    /** Someone tried to open a blocked app (at most once per 2 minutes per app). */
    public static synchronized void attempt(Context c, String pkg, String reason) {
        long now = System.currentTimeMillis();
        Long last = lastAttempt.get(pkg);
        if (last != null && now - last < 120000) return;
        lastAttempt.put(pkg, now);
        String why = reason == null || reason.isEmpty() ? "" : " (" + reason.replaceAll("^\\S+ ", "") + ")";
        add(c, "🔒", "أحد حاول يفتح " + Apps.label(c, pkg) + why, true);
    }
}
