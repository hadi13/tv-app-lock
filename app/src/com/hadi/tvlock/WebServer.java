package com.hadi.tvlock;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Local control panel for the phone (same Wi-Fi), protected by the PIN. */
public class WebServer extends Thread {
    public static final int PORT = 8787;

    private final LockService svc;
    private ServerSocket ss;
    private volatile boolean running = true;
    private final ExecutorService pool = Executors.newFixedThreadPool(4);
    private byte[] panel;

    private static int fails = 0;
    private static long blockedUntil = 0;

    public WebServer(LockService s) {
        super("tvlock-web");
        svc = s;
        setDaemon(true);
    }

    public void shutdown() {
        running = false;
        try { if (ss != null) ss.close(); } catch (IOException ignored) {}
        pool.shutdownNow();
    }

    @Override
    public void run() {
        try {
            ss = new ServerSocket(PORT);
            while (running) {
                final Socket s = ss.accept();
                pool.execute(new Runnable() {
                    @Override public void run() { handle(s); }
                });
            }
        } catch (IOException ignored) {
        }
    }

    // ---------- HTTP plumbing ----------

    private static String readLine(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') break;
            if (c != '\r') sb.append((char) c);
            if (sb.length() > 8192) break;
        }
        return sb.toString();
    }

    private void handle(Socket s) {
        try {
            s.setSoTimeout(10000);
            InputStream in = new BufferedInputStream(s.getInputStream());
            String req = readLine(in);
            if (req.isEmpty()) return;
            String[] parts = req.split(" ");
            String method = parts[0];
            String target = parts.length > 1 ? parts[1] : "/";
            Map<String, String> headers = new HashMap<String, String>();
            String line;
            while (!(line = readLine(in)).isEmpty()) {
                int i = line.indexOf(':');
                if (i > 0) headers.put(line.substring(0, i).trim().toLowerCase(), line.substring(i + 1).trim());
            }
            String body = "";
            String cl = headers.get("content-length");
            if (cl != null) {
                int n = Math.min(Integer.parseInt(cl.trim()), 16384);
                byte[] b = new byte[n];
                int r = 0;
                while (r < n) { int k = in.read(b, r, n - r); if (k < 0) break; r += k; }
                body = new String(b, 0, r, "UTF-8");
            }
            String path = target;
            String query = "";
            int q = target.indexOf('?');
            if (q >= 0) { path = target.substring(0, q); query = target.substring(q + 1); }
            if (path.startsWith("/debug/") && s.getInetAddress().isLoopbackAddress()) {
                debug(s.getOutputStream(), path);
                return;
            }
            route(s.getOutputStream(), method, path, params(query), body, headers);
        } catch (Exception ignored) {
        } finally {
            try { s.close(); } catch (IOException ignored) {}
        }
    }

    private static Map<String, String> params(String s) {
        Map<String, String> m = new HashMap<String, String>();
        if (s == null || s.isEmpty()) return m;
        for (String kv : s.split("&")) {
            int i = kv.indexOf('=');
            try {
                if (i > 0) m.put(URLDecoder.decode(kv.substring(0, i), "UTF-8"),
                        URLDecoder.decode(kv.substring(i + 1), "UTF-8"));
            } catch (Exception ignored) {}
        }
        return m;
    }

    private boolean authed(Map<String, String> h) {
        String c = h.get("cookie");
        if (c == null) return false;
        String tok = Store.webToken(svc);
        for (String part : c.split(";")) if (part.trim().equals("t=" + tok)) return true;
        return false;
    }

    private static void send(OutputStream o, int code, String type, byte[] data, String extra) throws IOException {
        String status = code == 200 ? "OK" : code == 401 ? "Unauthorized" : code == 404 ? "Not Found"
                : code == 429 ? "Too Many Requests" : code == 400 ? "Bad Request" : "Service Unavailable";
        StringBuilder h = new StringBuilder();
        h.append("HTTP/1.1 ").append(code).append(' ').append(status).append("\r\n");
        h.append("Content-Type: ").append(type).append("\r\n");
        h.append("Content-Length: ").append(data.length).append("\r\n");
        h.append(type.startsWith("image/png") ? "Cache-Control: max-age=86400\r\n" : "Cache-Control: no-store\r\n");
        h.append("Connection: close\r\n");
        if (extra != null) h.append(extra);
        h.append("\r\n");
        o.write(h.toString().getBytes("UTF-8"));
        o.write(data);
        o.flush();
    }

    private static void json(OutputStream o, int code, Object j, String extra) throws IOException {
        send(o, code, "application/json; charset=utf-8", j.toString().getBytes("UTF-8"), extra);
    }

    private static JSONObject err(String m) {
        JSONObject o = new JSONObject();
        try { o.put("error", m); } catch (Exception ignored) {}
        return o;
    }

    private byte[] panel() throws IOException {
        if (panel == null) {
            InputStream in = svc.getAssets().open("panel.html");
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) > 0) bo.write(b, 0, n);
            in.close();
            panel = bo.toByteArray();
        }
        return panel;
    }

    private String cookie() {
        return "Set-Cookie: t=" + Store.webToken(svc) + "; Path=/; Max-Age=31536000; HttpOnly; SameSite=Strict\r\n";
    }

    /** Diagnostics, only from the TV itself (reached through "adb forward"). */
    private void debug(OutputStream o, String path) throws Exception {
        if (path.equals("/debug/shot.jpg")) {
            LockService.Shot shot = svc.shot();
            if (shot.jpeg != null) send(o, 200, "image/jpeg", shot.jpeg, null);
            else send(o, 503, "text/plain; charset=utf-8", (shot.error + "\n" + svc.lastDiag).getBytes("UTF-8"), null);
        } else {
            String info = "diag: " + svc.lastDiag + "\ncurrent: " + LockService.current
                    + "\nsdk: " + android.os.Build.VERSION.SDK_INT + "\nmodel: " + android.os.Build.MODEL
                    + "\nchannel: " + LockService.liveChannel + "\nprogram: " + LockService.liveProgram
                    + "\n--- live dump ---\n" + LockService.liveDump;
            send(o, 200, "text/plain; charset=utf-8", info.getBytes("UTF-8"), null);
        }
    }

    // ---------- Routes ----------

    private void route(OutputStream o, String method, String path, Map<String, String> query,
                       String body, Map<String, String> h) throws Exception {
        if (!Store.webEnabled(svc)) {
            send(o, 503, "text/plain; charset=utf-8", "لوحة الجوال مطفية من إعدادات التطبيق على الشاشة".getBytes("UTF-8"), null);
            return;
        }
        if (path.equals("/")) { send(o, 200, "text/html; charset=utf-8", panel(), null); return; }

        if (path.equals("/api/login") && method.equals("POST")) {
            if (System.currentTimeMillis() < blockedUntil) { json(o, 429, err("محاولات كثيرة، انتظر دقيقة"), null); return; }
            String pin = new JSONObject(body).optString("pin", "");
            if (Store.checkPin(svc, pin)) {
                fails = 0;
                Events.rightPin("الجوال");
                json(o, 200, new JSONObject().put("ok", true), cookie());
            } else {
                boolean lockout = ++fails >= 5;
                if (lockout) { fails = 0; blockedUntil = System.currentTimeMillis() + 60000; }
                Events.wrongPin(svc, "لوحة الجوال", lockout);
                json(o, 401, err(lockout ? "محاولات كثيرة، انتظر دقيقة" : "رقم سري غلط"), null);
            }
            return;
        }
        if (path.equals("/api/logout")) {
            json(o, 200, new JSONObject().put("ok", true), "Set-Cookie: t=; Path=/; Max-Age=0\r\n");
            return;
        }
        if (!authed(h)) { json(o, 401, err("login"), null); return; }

        if (path.equals("/shot.jpg")) {
            LockService.Shot shot = svc.shot();
            if (shot.jpeg != null) send(o, 200, "image/jpeg", shot.jpeg, null);
            else send(o, 503, "text/plain; charset=utf-8", shot.error.getBytes("UTF-8"), null);
            return;
        }
        if (path.equals("/icon")) {
            byte[] png = Apps.iconPng(svc, query.get("pkg"));
            if (png != null) send(o, 200, "image/png", png, null);
            else send(o, 404, "text/plain", new byte[0], null);
            return;
        }
        if (path.equals("/api/state")) { json(o, 200, state(), null); return; }
        if (path.equals("/api/events")) {
            json(o, 200, new JSONObject().put("events", Events.list(svc)), null);
            return;
        }
        if (path.equals("/api/usage")) { json(o, 200, usage(), null); return; }

        if (!method.equals("POST")) { json(o, 404, err("not found"), null); return; }
        JSONObject in = body.isEmpty() ? new JSONObject() : new JSONObject(body);

        if (path.equals("/api/action")) {
            svc.remoteAction(in.optString("a"));
            Thread.sleep(500);
            json(o, 200, state(), null);
            return;
        }
        if (path.equals("/api/app")) {
            String pkg = in.optString("pkg", "");
            if (pkg.isEmpty() || !Rules.lockable(svc, pkg)) { json(o, 400, err("تطبيق غير معروف"), null); return; }
            if (in.has("locked")) Store.setLocked(svc, pkg, in.getBoolean("locked"));
            if (in.has("limit")) Store.setLimit(svc, pkg, in.getInt("limit"));
            if (in.has("open")) {
                int m = in.getInt("open");
                if (m == 0) Store.relock(svc, pkg);
                else if (m == -1) Store.unlock(svc, pkg, Store.FOREVER);
                else if (m > 0) Store.unlock(svc, pkg, System.currentTimeMillis() + m * 60000L);
            }
            svc.refresh();
            json(o, 200, state(), null);
            return;
        }
        if (path.equals("/api/lockset")) {
            // {all:true} or {none:true}
            if (in.optBoolean("all")) {
                java.util.Set<String> s = new java.util.HashSet<String>();
                for (Apps.Item it : Apps.list(svc)) s.add(it.pkg);
                Store.setLockedAll(svc, s);
            } else if (in.optBoolean("none")) {
                Store.setLockedAll(svc, new java.util.HashSet<String>());
            }
            svc.refresh();
            json(o, 200, state(), null);
            return;
        }
        if (path.equals("/api/bed")) {
            int st = in.optInt("start", Store.bedStart(svc)), en = in.optInt("end", Store.bedEnd(svc));
            st = Math.max(0, Math.min(1439, st));
            en = Math.max(0, Math.min(1439, en));
            Store.setBed(svc, in.optBoolean("on", Store.bedOn(svc)), st, en);
            svc.refresh();
            json(o, 200, state(), null);
            return;
        }
        if (path.equals("/api/ntfy")) {
            Store.setNtfy(svc, in.optBoolean("on", Store.ntfyOn(svc)), in.optString("topic", null));
            if (in.optBoolean("test")) Events.push(svc, "✅ تنبيه تجريبي من شاشة التلفزيون");
            json(o, 200, state(), null);
            return;
        }
        if (path.equals("/api/pin")) {
            String old = in.optString("old", ""), nw = in.optString("new", "");
            if (!Store.checkPin(svc, old)) { json(o, 400, err("الرقم السري الحالي غلط"), null); return; }
            if (!nw.matches("[0-9]{4,8}")) { json(o, 400, err("الرقم الجديد لازم يكون من ٤ إلى ٨ أرقام"), null); return; }
            Store.setPin(svc, nw);
            Events.add(svc, "🔑", "تغيّر الرقم السري من لوحة الجوال", false);
            json(o, 200, new JSONObject().put("ok", true), cookie());
            return;
        }
        json(o, 404, err("not found"), null);
    }

    // ---------- Data ----------

    private String remaining(String pkg) {
        long u = Store.until(svc, pkg);
        if (u == Store.FOREVER) return "مفتوح لين تقفله";
        if (u == Store.ONCE) return "مفتوح هذي المرة بس";
        if (u <= System.currentTimeMillis()) return "";
        return "باقي " + Usage.fmt(u - System.currentTimeMillis() + 59999);
    }

    private JSONObject state() throws Exception {
        JSONObject o = new JSONObject();
        String cur = LockService.current;
        String lt = LockActivity.showingTarget;
        JSONObject c = new JSONObject();
        c.put("pkg", cur == null ? "" : cur);
        c.put("label", cur == null ? "" : Apps.label(svc, cur));
        c.put("lockable", Rules.lockable(svc, cur));
        long left = cur == null ? -1 : Rules.timeLeft(svc, cur);
        c.put("left", left < 0 ? "" : Usage.fmt(left + 59999));
        c.put("open", cur != null && Store.until(svc, cur) != 0 && Store.isUnlocked(svc, cur));
        c.put("remain", cur == null ? "" : remaining(cur));
        boolean live = LockService.isLiveTv(cur);
        c.put("live", live);
        c.put("channel", live ? LockService.liveChannel : "");
        c.put("program", live ? LockService.liveProgram : "");
        o.put("cur", c);
        o.put("lockScreen", lt != null);
        o.put("lockPkg", lt == null ? "" : lt);
        o.put("lockLabel", lt == null ? "" : Apps.label(svc, lt));
        o.put("lockReason", lt == null ? "" : Rules.reason(svc, lt));

        JSONArray apps = new JSONArray();
        for (Apps.Item it : Apps.list(svc)) {
            JSONObject a = new JSONObject();
            a.put("pkg", it.pkg);
            a.put("label", it.label);
            a.put("locked", Store.isLocked(svc, it.pkg));
            a.put("limit", Store.limit(svc, it.pkg));
            a.put("used", Usage.today(svc, it.pkg) / 60000);
            long u = Store.until(svc, it.pkg);
            a.put("open", u != 0 && Store.isUnlocked(svc, it.pkg));
            a.put("remain", remaining(it.pkg));
            a.put("blocked", Rules.blocked(svc, it.pkg));
            apps.put(a);
        }
        o.put("apps", apps);

        JSONObject b = new JSONObject();
        b.put("on", Store.bedOn(svc));
        b.put("start", Store.bedStart(svc));
        b.put("end", Store.bedEnd(svc));
        b.put("active", Rules.inBedtime(svc));
        o.put("bed", b);

        JSONObject n = new JSONObject();
        n.put("on", Store.ntfyOn(svc));
        n.put("topic", Store.ntfyTopic(svc));
        o.put("ntfy", n);
        o.put("lastEvent", Events.lastId(svc));
        o.put("serviceOn", LockService.isEnabled(svc));
        return o;
    }

    private JSONObject usage() throws Exception {
        JSONArray days = new JSONArray();
        long now = System.currentTimeMillis();
        for (int i = 0; i < 7; i++) {
            String d = Usage.day(now - i * 86400000L);
            Map<String, Long> m = Usage.forDay(svc, d);
            List<Map.Entry<String, Long>> es = new ArrayList<Map.Entry<String, Long>>(m.entrySet());
            Collections.sort(es, new Comparator<Map.Entry<String, Long>>() {
                @Override public int compare(Map.Entry<String, Long> a, Map.Entry<String, Long> b) {
                    return b.getValue().compareTo(a.getValue());
                }
            });
            long total = 0;
            JSONArray apps = new JSONArray();
            for (Map.Entry<String, Long> e : es) {
                total += e.getValue();
                if (e.getValue() < 60000) continue;
                JSONObject a = new JSONObject();
                a.put("pkg", e.getKey());
                a.put("label", Apps.label(svc, e.getKey()));
                a.put("min", e.getValue() / 60000);
                apps.put(a);
            }
            JSONObject day = new JSONObject();
            day.put("date", d);
            day.put("total", total / 60000);
            day.put("apps", apps);
            days.put(day);
        }
        return new JSONObject().put("days", days);
    }

    public static String localIp() {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a instanceof Inet4Address && a.isSiteLocalAddress()) return a.getHostAddress();
                }
            }
        } catch (Exception ignored) {}
        return null;
    }
}
