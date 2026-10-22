package com.pdzdh.calcutool.comtrade;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 基波相量图（向量）。 */
public class PhasorChart extends View {

    private String[] labels = new String[0];
    private double[] mags = new double[0];
    private double[] norms = new double[0];
    private double[] angles = new double[0];
    private String[] units = new String[0];
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path head = new Path();
    private final float density;
    private int[] colors = new int[0];

    public PhasorChart(Context c) {
        super(c);
        density = c.getResources().getDisplayMetrics().density;
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        text.setTypeface(Typeface.MONOSPACE);
        text.setTextAlign(Paint.Align.LEFT);
    }

    private float dp(float v) { return v * density; }

    public void setData(String[] labels, double[] mags, double[] norms, double[] angles,
                        String[] units, int[] colors) {
        this.colors = colors == null ? new int[0] : colors;
        this.units = units == null ? new String[0] : units;
        this.labels = labels == null ? new String[0] : labels;
        this.mags = mags == null ? new double[0] : mags;
        this.norms = norms == null ? new double[0] : norms;
        this.angles = angles == null ? new double[0] : angles;
        invalidate();
    }

    private static boolean overlaps(List<android.graphics.RectF> list, android.graphics.RectF box) {
        for (android.graphics.RectF r : list) {
            if (android.graphics.RectF.intersects(r, box)) return true;
        }
        return false;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth(), h = getHeight();
        float cx = w / 2f, cy = h / 2f;
        float r = Math.min(w, h) / 2f * 0.72f;

        stroke.setStrokeWidth(dp(0.8f));
        stroke.setColor(0x22FFFFFF);
        canvas.drawCircle(cx, cy, r, stroke);
        canvas.drawCircle(cx, cy, r / 2f, stroke);
        canvas.drawLine(cx - r, cy, cx + r, cy, stroke);
        canvas.drawLine(cx, cy - r, cx, cy + r, stroke);

        text.setTextSize(dp(8));
        text.setColor(0x8A948EAC);
        text.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("0°", cx + r + dp(9), cy + dp(3), text);
        canvas.drawText("90°", cx, cy - r - dp(5), text);
        canvas.drawText("180°", cx - r - dp(11), cy + dp(3), text);
        canvas.drawText("270°", cx, cy + r + dp(11), text);

        List<android.graphics.RectF> placed = new ArrayList<>();
        for (int i = 0; i < mags.length && i < labels.length; i++) {
            double deg = Math.toRadians(angles[i]);
            double rel = i < norms.length ? norms[i] : mags[i];
            float len = (float) (r * 0.92 * Math.max(0.08, Math.min(1, rel)));
            float ex = (float) (cx + len * Math.cos(deg));
            float ey = (float) (cy - len * Math.sin(deg));
            int color = i < colors.length ? colors[i] : 0xFFA387FF;

            boolean isCurrent = i < units.length && units[i] != null && units[i].contains("A");
            stroke.setColor(color);
            stroke.setStrokeWidth(isCurrent ? dp(1.6f) : dp(1.8f));
            stroke.setPathEffect(isCurrent
                    ? new android.graphics.DashPathEffect(new float[]{dp(4), dp(3)}, 0) : null);
            canvas.drawLine(cx, cy, ex, ey, stroke);
            stroke.setPathEffect(null);

            // 箭头
            float ang = (float) Math.atan2(ey - cy, ex - cx);
            float hl = dp(6), hw = dp(2.8f);
            float bx = ex - hl * (float) Math.cos(ang);
            float by = ey - hl * (float) Math.sin(ang);
            float px = -hw * (float) Math.sin(ang);
            float py = hw * (float) Math.cos(ang);
            head.reset();
            head.moveTo(ex, ey);
            head.lineTo(bx + px, by + py);
            head.lineTo(bx - px, by - py);
            head.close();
            fill.setColor(color);
            canvas.drawPath(head, fill);

            // 标签沿箭头方向外推，撞车时继续外推，保证读数可读
            text.setTextSize(dp(8.5f));
            String name = labels[i];
            String val = String.format(Locale.US, "%.1f∠%.0f°", mags[i], angles[i]);
            float lw = Math.max(text.measureText(name), text.measureText(val));
            float dx = (float) Math.cos(deg), dy = (float) -Math.sin(deg);
            float lx = ex + dx * dp(6);
            float ly = ey + dy * dp(6);
            android.graphics.RectF box = new android.graphics.RectF(
                    lx - lw / 2 - dp(2), ly - dp(11), lx + lw / 2 + dp(2), ly + dp(4));
            int guard = 0;
            while (overlaps(placed, box) && guard < 5) {
                lx += dx * dp(10);
                ly += dy * dp(10);
                box.offset(dx * dp(10), dy * dp(10));
                guard++;
            }
            placed.add(box);
            text.setColor(color);
            text.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(name, lx, ly - dp(3), text);
            text.setColor(0xAAECE9F5);
            text.setTextSize(dp(7.2f));
            canvas.drawText(val, lx, ly + dp(7), text);
        }
    }
}
