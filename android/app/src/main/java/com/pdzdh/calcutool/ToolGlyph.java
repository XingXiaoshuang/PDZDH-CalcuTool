package com.pdzdh.calcutool;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

/** 工具箱卡片上的小图形，全部用 Canvas 绘制，避免位图资源。 */
public class ToolGlyph extends View {

    public static final int PHASOR = 0;
    public static final int QR = 1;
    public static final int WAVE = 2;
    public static final int GAME = 3;

    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final float density;
    private int kind = PHASOR;

    public ToolGlyph(Context c, int kind) {
        super(c);
        this.kind = kind;
        density = c.getResources().getDisplayMetrics().density;
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        fill.setStyle(Paint.Style.FILL);
    }

    private float dp(float v) { return v * density; }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth(), h = getHeight();
        float cx = w / 2f, cy = h / 2f;
        float r = Math.min(w, h) / 2f * 0.82f;
        if (kind == PHASOR) drawPhasor(canvas, cx, cy, r);
        else if (kind == QR) drawQr(canvas, cx, cy, r);
        else if (kind == GAME) drawGame(canvas, cx, cy, r);
        else drawWave(canvas, cx, cy, r);
    }

    private void drawPhasor(Canvas canvas, float cx, float cy, float r) {
        stroke.setStrokeWidth(dp(1.6f));
        stroke.setColor(0x33FFFFFF);
        canvas.drawCircle(cx, cy, r * 0.92f, stroke);

        int[] colors = {Pal.UA, Pal.UB, Pal.UC};
        for (int i = 0; i < 3; i++) {
            double deg = Math.toRadians(90 - i * 120);
            float ex = (float) (cx + r * 0.82f * Math.cos(deg));
            float ey = (float) (cy - r * 0.82f * Math.sin(deg));
            stroke.setColor(colors[i]);
            stroke.setStrokeWidth(dp(2.2f));
            canvas.drawLine(cx, cy, ex, ey, stroke);
            fill.setColor(colors[i]);
            canvas.drawCircle(ex, ey, dp(2.6f), fill);
        }
        fill.setColor(0xFFF2EFFA);
        canvas.drawCircle(cx, cy, dp(2.2f), fill);
    }

    private void drawQr(Canvas canvas, float cx, float cy, float r) {
        float s = r * 1.5f;
        float left = cx - s / 2f, top = cy - s / 2f;
        stroke.setColor(0x66FFFFFF);
        stroke.setStrokeWidth(dp(1.2f));
        canvas.drawRoundRect(new RectF(left, top, left + s, top + s), dp(4), dp(4), stroke);

        stroke.setColor(Pal.ACCENT_TEAL);
        stroke.setStrokeWidth(dp(2.2f));
        float a = s * 0.28f;
        canvas.drawLine(left, top + a, left, top, stroke);
        canvas.drawLine(left, top, left + a, top, stroke);
        canvas.drawLine(left + s, top + a, left + s, top, stroke);
        canvas.drawLine(left + s, top, left + s - a, top, stroke);
        canvas.drawLine(left, top + s - a, left, top + s, stroke);
        canvas.drawLine(left, top + s, left + a, top + s, stroke);
        canvas.drawLine(left + s, top + s - a, left + s, top + s, stroke);
        canvas.drawLine(left + s, top + s, left + s - a, top + s, stroke);

        fill.setColor(Pal.ACCENT_TEAL);
        float d = dp(2.4f);
        float step = s * 0.22f;
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                if ((i + j) % 2 == 0) {
                    canvas.drawCircle(cx - step + i * step, cy - step + j * step, d, fill);
                }
            }
        }
    }

    private void drawWave(Canvas canvas, float cx, float cy, float r) {
        stroke.setColor(0x22FFFFFF);
        stroke.setStrokeWidth(dp(1f));
        for (int i = -1; i <= 1; i++) {
            canvas.drawLine(cx - r, cy + i * r * 0.45f, cx + r, cy + i * r * 0.45f, stroke);
        }

        path.reset();
        for (int i = 0; i <= 60; i++) {
            float t = i / 60f;
            float x = cx - r + t * 2 * r;
            float y = (float) (cy - Math.sin(t * Math.PI * 4) * r * 0.5f
                    * (1 - 0.35f * t));
            if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
        }
        stroke.setColor(Pal.ACCENT_AMBER);
        stroke.setStrokeWidth(dp(1.8f));
        canvas.drawPath(path, stroke);

        stroke.setColor(Pal.ACCENT_PURPLE);
        stroke.setStrokeWidth(dp(1.2f));
        canvas.drawLine(cx - r, cy + r * 0.7f, cx + r, cy + r * 0.7f, stroke);
    }

    /** 转刀割草：旋转刀片 + 草地。 */
    private void drawGame(Canvas canvas, float cx, float cy, float r) {
        float hubY = cy - r * 0.18f;
        stroke.setColor(0x33FFFFFF);
        stroke.setStrokeWidth(dp(1.1f));
        canvas.drawCircle(cx, hubY, r * 0.86f, stroke);

        float br = r * 0.72f;
        RectF oval = new RectF(cx - br, hubY - br, cx + br, hubY + br);
        stroke.setColor(0xFFE8F0FF);
        stroke.setStrokeWidth(dp(2.4f));
        canvas.drawArc(oval, 200, 118, false, stroke);
        canvas.drawArc(oval, 20, 118, false, stroke);

        fill.setColor(Pal.ACCENT_AMBER);
        canvas.drawCircle(cx, hubY, dp(2.6f), fill);

        stroke.setColor(0xFF53DAAF);
        stroke.setStrokeWidth(dp(1.5f));
        for (int i = -1; i <= 1; i++) {
            float bx = cx + i * r * 0.36f;
            canvas.drawLine(bx, cy + r * 0.82f, bx + i * dp(1.5f), cy + r * 0.42f, stroke);
        }
    }

    /** 卡片底衬：圆角方块 + 轻微渐变。 */
    static int[] palette(int kind) {
        if (kind == QR) return new int[]{0x1F53DAAF, 0x0A53DAAF};
        if (kind == WAVE) return new int[]{0x1FFFB955, 0x0AFFB955};
        if (kind == GAME) return new int[]{0x1FFF6E6E, 0x0AFF6E6E};
        return new int[]{0x1FA387FF, 0x0AA387FF};
    }

    static Typeface mono() {
        return Typeface.MONOSPACE;
    }
}
