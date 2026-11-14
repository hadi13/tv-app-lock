package com.hadi.tvlock;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.hardware.HardwareBuffer;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Watches which app is in front: shows the lock screen, counts watch time,
 * applies bedtime and daily limits, warns before time runs out, and runs the phone server.
 */
public class LockService extends AccessibilityService {
    static volatile String current;
    static volatile LockService instance;

    // Live TV channel read from the on-screen channel bar
    static volatile String liveChannel = "";
    static volatile String liveProgram = "";
    static volatile String liveDump = "";
    static volatile long liveAt;
    private String loggedChannel = "";
    private final Runnable scan = new Runnable() {
        @Override public void run() { scanLive(); }
    };

    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            tick();
            h.postDelayed(this, 10000);
        }
    };

    private WebServer web;
    private final Executor shotExec = Executors.newSingleThreadExecutor();
    private byte[] lastJpeg;
    private long lastShotAt;

    // Watch-time tracking
    private String trackPkg;
    private long trackSince;
    private boolean screenOn = true;
    private String warned = "";
    private View banner;

    private final BroadcastReceiver screenRx = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if (Intent.ACTION_SCREEN_OFF.equals(i.getAction())) {
                flushUsage();
                screenOn = false;
            } else if (Intent.ACTION_SCREEN_ON.equals(i.getAction())) {
                screenOn = true;
                trackSince = System.currentTimeMillis();
            }
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        // After a reboot or restart, "this time only" unlocks do not carry over.
        Store.relockOnceExcept(this, "");
        instance = this;
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_SCREEN_ON);
        try { registerReceiver(screenRx, f); } catch (Exception ignored) {}
        updateServer();
        // Find the app already on screen (after a restart no event arrives until something changes).
        h.postDelayed(new Runnable() {
            @Override public void run() {
                if (current != null) return;
                try {
                    android.view.accessibility.AccessibilityNodeInfo r = getRootInActiveWindow();
                    if (r != null && r.getPackageName() != null) {
                        String p = r.getPackageName().toString();
                        if (!p.equals(getPackageName()) && !ignorable(p)) {
                            current = p;
                            trackPkg = Rules.lockable(LockService.this, p) ? p : null;
                            trackSince = System.currentTimeMillis();
                            if (isLiveTv(p)) scanLive();
                            check();
                        }
                    }
                } catch (Exception ignored) {}
            }
        }, 1500);
        h.removeCallbacks(tick);
        h.postDelayed(tick, 10000);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e == null) return;
        CharSequence cs = e.getPackageName();
        if (cs == null) return;
        String pkg = cs.toString();
        if (e.getEventType() == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            if (isLiveTv(pkg)) { h.removeCallbacks(scan); h.postDelayed(scan, 400); }
            return;
        }
        if (e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        if (isLiveTv(pkg)) { h.removeCallbacks(scan); h.postDelayed(scan, 600); }
        if (pkg.equals(getPackageName())) {
            // Our lock screen or settings: the app underneath is not being watched.
            flushUsage();
            trackPkg = null;
            CharSequence cls = e.getClassName();
            if (cls != null && cls.toString().endsWith("HomeActivity")) {
                // Our home screen is in front: the previous app was left.
                Store.relockOnceExcept(this, pkg);
                current = pkg;
            }
            return;
        }
        if (ignorable(pkg)) return;
        Store.relockOnceExcept(this, pkg);
        flushUsage();
        trackPkg = Rules.lockable(this, pkg) ? pkg : null;
        trackSince = System.currentTimeMillis();
        current = pkg;
        check();
    }

    private boolean ignorable(String pkg) {
        if (pkg.equals("android") || pkg.equals("com.android.systemui")) return true;
        String l = pkg.toLowerCase();
        return l.contains("inputmethod") || l.contains("keyboard") || l.contains(".ime")
                || l.contains("gboard") || l.contains("leanbackime");
    }

    static boolean isLiveTv(String pkg) {
        if (pkg == null) return false;
        String l = pkg.toLowerCase();
        return l.contains("livetv") || l.contains("tvinput") || l.contains(".dtv") || l.contains("tuner")
                || l.equals("com.google.android.tv") || l.equals("com.android.tv");
    }

    /** Reads the text of the Live TV channel bar (channel number/name, program). */
    private void scanLive() {
        try {
            java.util.List<String[]> items = new java.util.ArrayList<String[]>();
            java.util.List<android.view.accessibility.AccessibilityWindowInfo> ws = getWindows();
            for (android.view.accessibility.AccessibilityWindowInfo w : ws) {
                android.view.accessibility.AccessibilityNodeInfo r = w.getRoot();
                if (r == null || r.getPackageName() == null) continue;
                if (!isLiveTv(r.getPackageName().toString())) continue;
                collect(r, items, 0);
            }
            if (items.isEmpty()) {
                android.view.accessibility.AccessibilityNodeInfo r = getRootInActiveWindow();
                if (r != null && r.getPackageName() != null && isLiveTv(r.getPackageName().toString())) collect(r, items, 0);
            }
            if (items.isEmpty()) return;
            StringBuilder dump = new StringBuilder();
            String num = "", name = "", prog = "", fallback = "";
            for (String[] it : items) {
                String id = it[0] == null ? "" : it[0].toLowerCase();
                String t = it[1].trim();
                if (dump.length() < 3000) dump.append(it[0]).append(" = ").append(t).append('\n');
                String sid = id.contains("/") ? id.substring(id.indexOf('/') + 1) : id;
                boolean isTime = t.matches(".*\\d{1,2}:\\d{2}.*");
                boolean isNum = t.matches("\\d{1,4}(-\\d{1,3})?");
                if (sid.contains("channel") || sid.contains("chan") || sid.startsWith("ch_") || sid.contains("_ch")) {
                    if ((sid.contains("num") || sid.contains("no") || sid.contains("index")) && isNum) { if (num.isEmpty()) num = t; continue; }
                    if (!isNum && !isTime && name.isEmpty()) { name = t; continue; }
                }
                if ((sid.contains("program") || sid.contains("event") || sid.contains("epg") || sid.contains("show"))
                        && !isTime && prog.isEmpty() && !isNum) { prog = t; continue; }
                if (isNum && num.isEmpty() && t.length() <= 4) { num = t; continue; }
                if (!isNum && !isTime && fallback.isEmpty() && t.length() >= 2 && t.length() <= 40) fallback = t;
            }
            liveDump = dump.toString();
            if (name.isEmpty()) name = fallback;
            String ch = (num + " " + name).trim();
            if (!ch.isEmpty()) {
                if (!ch.equals(liveChannel)) liveAt = System.currentTimeMillis();
                liveChannel = ch;
                liveProgram = prog;
            }
        } catch (Exception ignored) {}
    }

    private void collect(android.view.accessibility.AccessibilityNodeInfo n, java.util.List<String[]> out, int depth) {
        if (n == null || depth > 30 || out.size() > 300) return;
        CharSequence t = n.getText();
        if (t == null || t.length() == 0) t = n.getContentDescription();
        if (t != null && t.length() > 0 && t.length() <= 60 && n.isVisibleToUser()) {
            out.add(new String[]{n.getViewIdResourceName(), t.toString()});
        }
        for (int i = 0; i < n.getChildCount(); i++) collect(n.getChild(i), out, depth + 1);
    }

    private void flushUsage() {
        long now = System.currentTimeMillis();
        if (trackPkg != null && screenOn && trackSince > 0) {
            long d = now - trackSince;
            if (d > 0 && d <= 120000) Usage.add(this, trackPkg, d);
        }
        trackSince = now;
    }

    private void tick() {
        flushUsage();
        Usage.cleanup(this);
        // Bedtime start: close everything that was opened earlier.
        boolean bed = Rules.inBedtime(this);
        if (bed && !Store.bedApplied(this)) {
            Store.setBedApplied(this, true);
            Store.relockAll(this);
            Events.add(this, "🌙", "بدأ وقت النوم وانقفلت كل التطبيقات", true);
        } else if (!bed && Store.bedApplied(this)) {
            Store.setBedApplied(this, false);
        }
        check();
        countdown();
        String cur = current;
        if (isLiveTv(cur) && screenOn && !liveChannel.isEmpty() && !liveChannel.equals(loggedChannel)
                && System.currentTimeMillis() - liveAt >= 60000) {
            loggedChannel = liveChannel;
            Events.add(this, "📺", "يشاهد قناة " + liveChannel, false);
        }
    }

    void check() {
        String pkg = current;
        if (pkg == null) return;
        if (Rules.blocked(this, pkg)) {
            long u = Store.until(this, pkg);
            if (u > 0 && u <= System.currentTimeMillis()) Store.relock(this, pkg); // expired timer
            if (pkg.equals(LockActivity.showingTarget)) return; // already on screen
            showLock(pkg);
        }
    }

    private void showLock(String pkg) {
        Intent i = new Intent(this, LockActivity.class);
        i.putExtra(LockActivity.EXTRA_PKG, pkg);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_NO_ANIMATION
                | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        startActivity(i);
    }

    // ---------- 5-minute / 1-minute warning on the TV ----------

    private void countdown() {
        String pkg = trackPkg;
        if (pkg == null || !screenOn || Rules.blocked(this, pkg)) return;
        long left = Rules.timeLeft(this, pkg);
        if (left < 0 || left > 5 * 60000L + 5000) return;
        long deadlineMin = (System.currentTimeMillis() + left) / 60000;
        String key;
        String msg;
        if (left <= 70000) {
            key = pkg + deadlineMin + "-1";
            msg = "⏳ باقي دقيقة وحدة وينقفل " + Apps.label(this, pkg);
        } else {
            key = pkg + deadlineMin + "-5";
            msg = "⏳ باقي " + Math.max(2, (left + 30000) / 60000) + " دقايق وينقفل " + Apps.label(this, pkg);
        }
        if (key.equals(warned)) return;
        warned = key;
        showBanner(msg);
    }

    private void showBanner(String text) {
        try {
            final WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
            hideBanner(wm);
            TextView tv = new TextView(this);
            tv.setText(text);
            tv.setTextSize(24);
            tv.setTextColor(0xFFFFFFFF);
            tv.setTextDirection(View.TEXT_DIRECTION_RTL);
            int pd = Ui.dp(this, 18);
            tv.setPadding(pd, Ui.dp(this, 12), pd, Ui.dp(this, 12));
            GradientDrawable g = new GradientDrawable();
            g.setColor(0xE60F172A);
            g.setCornerRadius(Ui.dp(this, 16));
            g.setStroke(Ui.dp(this, 3), 0xFFFBBF24);
            tv.setBackground(g);
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            lp.y = Ui.dp(this, 30);
            wm.addView(tv, lp);
            banner = tv;
            h.postDelayed(new Runnable() {
                @Override public void run() { hideBanner(wm); }
            }, 15000);
        } catch (Exception ignored) {}
    }

    private void hideBanner(WindowManager wm) {
        if (banner != null) {
            try { wm.removeView(banner); } catch (Exception ignored) {}
            banner = null;
        }
    }

    // ---------- Phone server ----------

    /** Starts or stops the phone server to match the setting. */
    public void updateServer() {
        boolean on = Store.webEnabled(this);
        if (on && web == null) { web = new WebServer(this); web.start(); }
        else if (!on && web != null) { web.shutdown(); web = null; }
    }

    /** Re-check the app in front after a change from the phone. */
    public void refresh() {
        h.post(new Runnable() { @Override public void run() { check(); } });
    }

    public static class Shot {
        public byte[] jpeg;
        public String error;
    }

    public synchronized Shot shot() {
        Shot r = new Shot();
        long now = System.currentTimeMillis();
        if (lastJpeg != null && now - lastShotAt < 700) { r.jpeg = lastJpeg; return r; }
        if (Build.VERSION.SDK_INT < 30) { r.error = "نظام الشاشة قديم وما يدعم التصوير (يحتاج أندرويد 11 أو أحدث)"; return r; }
        android.os.PowerManager pm = (android.os.PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null && !pm.isInteractive()) { r.error = "😴 الشاشة مطفية أو في وضع الاستعداد"; return r; }
        final CountDownLatch done = new CountDownLatch(1);
        final byte[][] out = new byte[1][];
        final String[] err = new String[1];
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, shotExec, new TakeScreenshotCallback() {
                @Override public void onSuccess(ScreenshotResult res) {
                    try {
                        Bitmap bm = convert(res);
                        if (bm != null) {
                            ByteArrayOutputStream bo = new ByteArrayOutputStream();
                            bm.compress(Bitmap.CompressFormat.JPEG, 65, bo);
                            bm.recycle();
                            out[0] = bo.toByteArray();
                        } else {
                            String cur = current == null ? "" : current.toLowerCase();
                            if (cur.contains("livetv") || cur.contains("tvinput") || cur.contains("dtv")
                                    || cur.equals("com.google.android.tv") || cur.contains("tuner")) {
                                err[0] = "📺 القنوات المباشرة ما تنصوّر، بس تقدر تشوف اسم القناة تحت.";
                            } else {
                                err[0] = "⬛ الصورة سودا: غالباً فيديو أو قناة محمية من التصوير. اسم التطبيق يظهر تحت.";
                            }
                        }
                    } catch (Throwable t) {
                        lastDiag += " ex=" + t;
                        err[0] = "تعذر تجهيز الصورة";
                    }
                    done.countDown();
                }

                @Override public void onFailure(int code) {
                    if (code == 3) err[0] = "BUSY";
                    else if (code == 5 || code == 6) err[0] = "التطبيق الحالي يمنع تصوير الشاشة (مثل نتفلكس وشاهد)، بس تقدر تشوف اسمه تحت.";
                    else err[0] = "ما قدرت أصوّر الشاشة (رمز " + code + ")";
                    done.countDown();
                }
            });
            done.await(3, TimeUnit.SECONDS);
        } catch (Throwable t) {
            err[0] = "ما قدرت أصوّر الشاشة";
        }
        if (out[0] != null) {
            lastJpeg = out[0];
            lastShotAt = System.currentTimeMillis();
            r.jpeg = lastJpeg;
        } else if ("BUSY".equals(err[0]) && lastJpeg != null) {
            r.jpeg = lastJpeg;
        } else {
            r.error = err[0] == null || "BUSY".equals(err[0]) ? "الشاشة ما ردّت" : err[0];
        }
        return r;
    }

    /** Info about the last capture, for /debug/info (only reachable through adb). */
    volatile String lastDiag = "";
    private int goodMethod = -1;
    private static final int OUT_W = 960;

    private static boolean isBlack(Bitmap b) {
        int w = b.getWidth(), hh = b.getHeight();
        int bright = 0;
        for (int y = 1; y < 12; y++) {
            for (int x = 1; x < 16; x++) {
                int c = b.getPixel(x * w / 16, y * hh / 12);
                int m = Math.max(Math.max((c >> 16) & 255, (c >> 8) & 255), c & 255);
                if (m > 10) bright++;
            }
        }
        return bright < 3;
    }

    private static Bitmap scale(Bitmap sw) {
        int hgt = Math.max(1, sw.getHeight() * OUT_W / Math.max(1, sw.getWidth()));
        Bitmap sc = Bitmap.createScaledBitmap(sw, OUT_W, hgt, true);
        if (sc != sw) sw.recycle();
        return sc;
    }

    /** Hardware screenshot -> normal bitmap; tries several ways because some TVs return black. */
    private Bitmap convert(ScreenshotResult res) {
        HardwareBuffer hb = res.getHardwareBuffer();
        StringBuilder d = new StringBuilder();
        d.append("fmt=").append(hb.getFormat()).append(" ").append(hb.getWidth()).append("x").append(hb.getHeight())
                .append(" cs=").append(res.getColorSpace() == null ? "null" : res.getColorSpace().getName());
        Bitmap result = null;
        int[] order = goodMethod >= 0 ? new int[]{goodMethod, 1, 2, 3} : new int[]{1, 2, 3};
        for (int m : order) {
            if (result != null) break;
            Bitmap b = null;
            try {
                if (m == 1) {
                    Bitmap hw = Bitmap.wrapHardwareBuffer(hb, res.getColorSpace());
                    b = scale(hw.copy(Bitmap.Config.ARGB_8888, false));
                    hw.recycle();
                } else if (m == 2) {
                    Bitmap hw = Bitmap.wrapHardwareBuffer(hb,
                            android.graphics.ColorSpace.get(android.graphics.ColorSpace.Named.SRGB));
                    b = scale(hw.copy(Bitmap.Config.ARGB_8888, false));
                    hw.recycle();
                } else {
                    b = viaImageReader(hb, res);
                }
                if (b == null) { d.append(" m").append(m).append("=null"); continue; }
                if (isBlack(b)) { d.append(" m").append(m).append("=black"); b.recycle(); continue; }
                d.append(" m").append(m).append("=ok");
                goodMethod = m;
                result = b;
            } catch (Throwable t) {
                d.append(" m").append(m).append("=err:").append(t.getClass().getSimpleName());
                if (b != null) b.recycle();
            }
        }
        hb.close();
        lastDiag = d.toString();
        return result;
    }

    /** Draws the screenshot with the GPU into an ImageReader and reads plain pixels back. */
    private Bitmap viaImageReader(HardwareBuffer hb, ScreenshotResult res) throws Exception {
        int w = OUT_W;
        int hgt = Math.max(1, hb.getHeight() * w / Math.max(1, hb.getWidth()));
        android.media.ImageReader ir = android.media.ImageReader.newInstance(w, hgt, PixelFormat.RGBA_8888, 2);
        try {
            Bitmap hw = Bitmap.wrapHardwareBuffer(hb, res.getColorSpace());
            android.view.Surface sf = ir.getSurface();
            android.graphics.Canvas cv = sf.lockHardwareCanvas();
            cv.drawColor(0xFF000000);
            android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG);
            cv.drawBitmap(hw, null, new android.graphics.Rect(0, 0, w, hgt), p);
            sf.unlockCanvasAndPost(cv);
            android.media.Image img = null;
            for (int i = 0; i < 50 && img == null; i++) {
                img = ir.acquireLatestImage();
                if (img == null) Thread.sleep(20);
            }
            hw.recycle();
            if (img == null) return null;
            android.media.Image.Plane pl = img.getPlanes()[0];
            int rowPx = pl.getRowStride() / pl.getPixelStride();
            Bitmap full = Bitmap.createBitmap(rowPx, hgt, Bitmap.Config.ARGB_8888);
            full.copyPixelsFromBuffer(pl.getBuffer());
            img.close();
            Bitmap crop = Bitmap.createBitmap(full, 0, 0, w, hgt);
            if (crop != full) full.recycle();
            return crop;
        } finally {
            ir.close();
        }
    }

    public void remoteAction(final String a) {
        if (a == null) return;
        h.post(new Runnable() {
            @Override public void run() {
                String cur = current;
                if (a.equals("home")) {
                    performGlobalAction(GLOBAL_ACTION_HOME);
                } else if (a.equals("back")) {
                    performGlobalAction(GLOBAL_ACTION_BACK);
                } else if (a.equals("lockall")) {
                    Store.relockAll(LockService.this);
                    check();
                } else if (a.equals("lockcurrent")) {
                    if (Rules.lockable(LockService.this, cur)) {
                        if (!Store.isLocked(LockService.this, cur)) Store.setLocked(LockService.this, cur, true);
                        Store.relock(LockService.this, cur);
                        check();
                    }
                } else if (a.startsWith("open")) {
                    String t = LockActivity.showingTarget;
                    if (t == null) return;
                    long until;
                    if (a.equals("openonce")) until = Store.ONCE;
                    else if (a.equals("open15")) until = System.currentTimeMillis() + 15 * 60000L;
                    else until = System.currentTimeMillis() + 3600000L;
                    Store.unlock(LockService.this, t, until);
                    LockActivity.remoteOpened(t);
                }
            }
        });
    }

    @Override public void onInterrupt() {}

    @Override
    public void onDestroy() {
        h.removeCallbacks(tick);
        flushUsage();
        try { unregisterReceiver(screenRx); } catch (Exception ignored) {}
        if (web != null) { web.shutdown(); web = null; }
        if (instance == this) instance = null;
        super.onDestroy();
    }

    public static boolean isEnabled(Context c) {
        String s = Settings.Secure.getString(c.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (s == null) return false;
        String me = new ComponentName(c, LockService.class).flattenToString();
        String meShort = new ComponentName(c, LockService.class).flattenToShortString();
        for (String part : s.split(":")) {
            if (part.equalsIgnoreCase(me) || part.equalsIgnoreCase(meShort)) return true;
        }
        return false;
    }
}
