package com.pdzdh.calcutool;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Polar phasor diagram, port of DrawPhasor()/DrawLegend() from the WinForm build. */
public class PhasorView extends View {

    private static final String[] NAMES =
            {"Ua", "Ub", "Uc", "Uab", "Ubc", "Uca", "Ia", "Ib", "Ic", "U1", "U2", "U0", "I1", "I2", "I0"};

    private PhasorCalc.Result result;

    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path arrow = new Path();
    private final float density;

    public PhasorView(Context c) {
        this(c, null);
    }

    public PhasorView(Context c, AttributeSet a) {
        super(c, a);
        density = c.getResources().getDisplayMetrics().density;
        stroke.setStyle(Paint.Style.STROKE);
        fill.setStyle(Paint.Style.FILL);
        text.setTypeface(Typeface.MONOSPACE);
    }

    private float dp(double v) { return (float) (v * density); }

    public void setResult(PhasorCalc.Result r) {
        this.result = r;
        if (r != null) {
            setContentDescription(String.format(Locale.US,
                    "Ua %.1f V, Ub %.1f V, Uc %.1f V", r.va.mag(), r.vb.mag(), r.vc.mag()));
        }
        invalidate();
    }

    private static int colorOf(String name) {
        switch (name) {
            case "Ua": return Pal.UA;
            case "Ub": return Pal.UB;
            case "Uc": return Pal.UC;
            case "Ia": return Pal.IA;
            case "Ib": return Pal.IB;
            case "Ic": return Pal.IC;
            case "Uab": return Pal.UAB;
            case "Ubc": return Pal.UBC;
            case "Uca": return Pal.UCA;
            default:
                if (name.endsWith("1")) return Pal.POS;
                if (name.endsWith("2")) return Pal.NEG;
                if (name.endsWith("0")) return Pal.ZER;
                return Pal.TEXT_PRIMARY;
        }
    }

    private static float strokeWidth(String name, float density) {
        if (name.startsWith("U") && name.length() == 2) return 2.0f * density;
        if (name.startsWith("U") && name.length() == 3) return 1.6f * density;
        return 1.1f * density;
    }

    private static boolean isCurrent(String name) { return name.startsWith("I"); }

    private static Complex valueOf(String name, PhasorCalc.Result r) {
        switch (name) {
            case "Ua": return r.va;
            case "Ub": return r.vb;
            case "Uc": return r.vc;
            case "Ia": return r.ia;
            case "Ib": return r.ib;
            case "Ic": return r.ic;
            case "Uab": return r.uab;
            case "Ubc": return r.ubc;
            case "Uca": return r.uca;
            case "U1": return r.v1.div(3);
            case "U2": return r.v2.div(3);
            case "U0": return r.v0.div(3);
            case "I1": return r.i1.div(3);
            case "I2": return r.i2.div(3);
            case "I0": return r.i0.div(3);
            default: return Complex.ZERO;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        PhasorCalc.Result r = result;
        if (r == null) return;

        float w = getWidth();
        float h = getHeight();
        float legendH = dp(86);
        float plotH = h - legendH;
        float cx = w / 2f;
        float cy = plotH / 2f;
        float radius = Math.min(w, plotH) / 2f * 0.80f;

        drawGrid(canvas, cx, cy, radius);
        drawVectors(canvas, cx, cy, radius, r);
        drawLegend(canvas, w, plotH);
    }

    private void drawGrid(Canvas canvas, float cx, float cy, float radius) {
        stroke.setPathEffect(new DashPathEffect(new float[]{dp(3), dp(4)}, 0));
        stroke.setStrokeWidth(dp(1));
        stroke.setColor(Color.argb(30, 255, 255, 255));
        for (int i = 1; i <= 4; i++) {
            float rr = radius / 4f * i;
            canvas.drawCircle(cx, cy, rr, stroke);
        }
        stroke.setPathEffect(null);

        stroke.setColor(Color.argb(45, 255, 255, 255));
        canvas.drawLine(cx, dp(2), cx, cy * 2 - dp(2), stroke);
        canvas.drawLine(dp(4), cy, canvas.getWidth() - dp(4), cy, stroke);

        stroke.setColor(Color.argb(22, 255, 255, 255));
        for (int deg = 0; deg < 360; deg += 30) {
            double rad = Math.toRadians(deg);
            canvas.drawLine(cx, cy,
                    (float) (cx + radius * Math.cos(rad)),
                    (float) (cy - radius * Math.sin(rad)), stroke);
        }

        text.setColor(Pal.TEXT_MUTED);
        text.setTextSize(dp(9));
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(Typeface.MONOSPACE);
        float outer = radius + dp(11);
        label(canvas, "0°", cx + outer, cy);
        label(canvas, "90°", cx, cy - outer);
        label(canvas, "180°", cx - outer, cy);
        label(canvas, "270°", cx, cy + outer);
    }

    private void label(Canvas canvas, String s, float x, float y) {
        Paint.FontMetricsInt fm = text.getFontMetricsInt();
        canvas.drawText(s, x, y - (fm.ascent + fm.descent) / 2f, text);
    }

    private void drawVectors(Canvas canvas, float cx, float cy, float radius, PhasorCalc.Result r) {
        double maxMag = 0;
        for (String n : NAMES) {
            double m = valueOf(n, r).mag();
            if (isCurrent(n)) m *= 10;
            maxMag = Math.max(maxMag, m);
        }
        double scale = maxMag == 0 ? 1 : radius / maxMag;

        // origin marker
        fill.setColor(Color.argb(220, 58, 54, 80));
        canvas.drawCircle(cx, cy, dp(3), fill);

        List<RectF> placed = new ArrayList<>();
        text.setTextSize(dp(10));
        text.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        text.setTextAlign(Paint.Align.LEFT);

        for (String n : NAMES) {
            Complex ph = valueOf(n, r);
            double mag = ph.mag();
            if (isCurrent(n)) mag *= 10;
            if (mag < 1e-9) continue;

            double ang = ph.arg();
            float ex = (float) (cx + scale * mag * Math.cos(ang));
            float ey = (float) (cy - scale * mag * Math.sin(ang));
            int col = colorOf(n);

            drawArrow(canvas, cx, cy, ex, ey, col, strokeWidth(n, density));

            fill.setColor(col);
            fill.setAlpha(200);
            canvas.drawCircle(ex, ey, dp(2.6), fill);
            fill.setAlpha(255);

            // label with simple vertical de-collision
            text.setColor(col);
            float tx = ex + dp(5);
            float ty = ey - dp(7);
            float tw = text.measureText(n);
            float th = text.getTextSize();
            RectF box = new RectF(tx - dp(1), ty - th, tx + tw + dp(1), ty + dp(2));
            int guard = 0;
            while (overlaps(placed, box) && guard < 6) {
                box.offset(0, -dp(11));
                guard++;
            }
            placed.add(box);
            canvas.drawText(n, box.left + dp(1), box.bottom - dp(2), text);
        }
    }

    private static boolean overlaps(List<RectF> list, RectF box) {
        for (RectF q : list) {
            if (RectF.intersects(q, box)) return true;
        }
        return false;
    }

    private void drawArrow(Canvas canvas, float x0, float y0, float x1, float y1, int color, float lw) {
        float ang = (float) Math.atan2(y1 - y0, x1 - x0);
        float headLen = dp(7);
        float headW = dp(3.2f);
        float bx = x1 - headLen * (float) Math.cos(ang);
        float by = y1 - headLen * (float) Math.sin(ang);

        stroke.setPathEffect(null);
        stroke.setStrokeWidth(lw);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setColor(color);
        canvas.drawLine(x0, y0, bx, by, stroke);

        float px = -headW * (float) Math.sin(ang);
        float py = headW * (float) Math.cos(ang);
        arrow.reset();
        arrow.moveTo(x1, y1);
        arrow.lineTo(bx + px, by + py);
        arrow.lineTo(bx - px, by - py);
        arrow.close();
        fill.setColor(color);
        fill.setStyle(Paint.Style.FILL);
        canvas.drawPath(arrow, fill);
    }

    private void drawLegend(Canvas canvas, float w, float plotH) {
        int[] cols = {Pal.UA, Pal.UB, Pal.UC, Pal.IA, Pal.IB, Pal.IC,
                      Pal.UAB, Pal.UBC, Pal.UCA, Pal.POS, Pal.NEG, Pal.ZER};
        String[] en = {"Phase Ua (V)", "Phase Ub (V)", "Phase Uc (V)", "Phase Ia (A)",
                       "Phase Ib (A)", "Phase Ic (A)", "Line Uab", "Line Ubc", "Line Uca",
                       "Positive seq", "Negative seq", "Zero seq"};
        String[] zh = {"A相电压 (V)", "B相电压 (V)", "C相电压 (V)", "A相电流 (A)",
                       "B相电流 (A)", "C相电流 (A)", "线电压 Uab", "线电压 Ubc", "线电压 Uca",
                       "正序", "负序", "零序"};
        String[] labels = Lang.isEn() ? en : zh;

        float padX = dp(10);
        float rowH = dp(12.5f);
        float panelW = w - dp(8) * 2;
        float panelH = rowH * 6 + dp(9);
        float left = dp(8);
        float top = plotH + dp(2);

        fill.setStyle(Paint.Style.FILL);
        fill.setColor(Color.argb(150, 13, 12, 17));
        RectF panel = new RectF(left, top, left + panelW, top + panelH);
        canvas.drawRoundRect(panel, dp(10), dp(10), fill);

        float colW = panelW / 2f;
        text.setTextSize(dp(8.6f));
        text.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));
        text.setTextAlign(Paint.Align.LEFT);

        for (int i = 0; i < cols.length; i++) {
            int c = i % 6;
            int row = i / 6;
            float x = left + dp(10) + row * colW;
            float y = top + dp(4) + c * rowH + rowH * 0.55f;

            stroke.setColor(cols[i]);
            stroke.setStrokeWidth(dp(2));
            stroke.setStrokeCap(Paint.Cap.ROUND);
            canvas.drawLine(x, y, x + dp(9), y, stroke);

            text.setColor(Color.argb(220, 236, 233, 245));
            Paint.FontMetricsInt fm = text.getFontMetricsInt();
            canvas.drawText(labels[i], x + dp(13), y - (fm.ascent + fm.descent) / 2f, text);
        }
    }
}
