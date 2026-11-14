package com.hadi.tvlock;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** Thin line icons drawn in code (24-unit grid), in the spirit of SF Symbols. */
public class IconView extends View {
    public static final int HOME = 0, TV = 1, LOCK = 2, GEAR = 3, MOON = 4, WARN = 5;

    private final int kind;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    public IconView(Context c, int kind, int color) {
        super(c);
        this.kind = kind;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setColor(color);
    }

    public void setColor(int color) { p.setColor(color); invalidate(); }

    @Override
    protected void onDraw(Canvas c) {
        float s = Math.min(getWidth(), getHeight()) / 24f;
        c.save();
        c.translate((getWidth() - 24 * s) / 2f, (getHeight() - 24 * s) / 2f);
        c.scale(s, s);
        p.setStrokeWidth(1.9f);
        path.reset();
        switch (kind) {
            case HOME:
                path.moveTo(3.5f, 10.5f); path.lineTo(12, 3.5f); path.lineTo(20.5f, 10.5f);
                c.drawPath(path, p);
                c.drawRoundRect(new RectF(5.5f, 9.5f, 18.5f, 20.5f), 2, 2, p);
                c.drawRoundRect(new RectF(10, 14, 14, 20.5f), 1, 1, p);
                break;
            case TV:
                c.drawRoundRect(new RectF(2.5f, 6, 21.5f, 18.5f), 3, 3, p);
                path.moveTo(8, 2.5f); path.lineTo(12, 6); path.lineTo(16, 2.5f);
                c.drawPath(path, p);
                c.drawLine(8, 21.5f, 16, 21.5f, p);
                break;
            case LOCK:
                c.drawRoundRect(new RectF(4.5f, 10.5f, 19.5f, 21), 3, 3, p);
                path.moveTo(8, 10.5f); path.lineTo(8, 7.5f);
                path.arcTo(new RectF(8, 3.5f, 16, 11.5f), 180, 180);
                path.lineTo(16, 10.5f);
                c.drawPath(path, p);
                break;
            case GEAR:
                c.drawCircle(12, 12, 3.2f, p);
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI / 4 * i;
                    c.drawLine((float) (12 + Math.cos(a) * 7), (float) (12 + Math.sin(a) * 7),
                            (float) (12 + Math.cos(a) * 9.5), (float) (12 + Math.sin(a) * 9.5), p);
                }
                c.drawCircle(12, 12, 7, p);
                break;
            case MOON:
                path.addArc(new RectF(3, 3, 21, 21), 60, 250);
                path.arcTo(new RectF(9, 1, 23, 15), 210, -150);
                c.drawPath(path, p);
                break;
            case WARN:
                path.moveTo(12, 3); path.lineTo(22, 20.5f); path.lineTo(2, 20.5f); path.close();
                c.drawPath(path, p);
                c.drawLine(12, 9.5f, 12, 14, p);
                c.drawPoint(12, 17.3f, p);
                break;
        }
        c.restore();
    }
}
