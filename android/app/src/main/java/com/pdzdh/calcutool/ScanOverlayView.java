package com.pdzdh.calcutool;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AnimationUtils;

/** Camera framing overlay: dimmed surround, corner brackets and a moving scan line. */
public class ScanOverlayView extends View {

    private final Paint dim = new Paint();
    private final Paint frame = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bracket = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private final float density;
    private float progress;

    public ScanOverlayView(Context c) {
        super(c);
        density = c.getResources().getDisplayMetrics().density;
        dim.setColor(0xB3000000);
        frame.setStyle(Paint.Style.STROKE);
        frame.setStrokeWidth(dp(1.2f));
        frame.setColor(0x40FFFFFF);
        bracket.setStyle(Paint.Style.STROKE);
        bracket.setStrokeWidth(dp(3.2f));
        bracket.setStrokeCap(Paint.Cap.ROUND);
        bracket.setColor(Pal.ACCENT_TEAL);
        line.setStrokeWidth(dp(2));
        line.setColor(Pal.ACCENT_TEAL);
        hint.setColor(0xCCECE9F5);
        hint.setTextSize(dp(12));
        hint.setTextAlign(Paint.Align.CENTER);
        hint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));
    }

    private float dp(float v) { return v * density; }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        float side = Math.min(w, h) * 0.68f;
        float left = (w - side) / 2f;
        float top = (h - side) / 2f - dp(24);
        box.set(left, top, left + side, top + side);

        // dim everything except the capture square
        canvas.drawRect(0, 0, w, box.top, dim);
        canvas.drawRect(0, box.bottom, w, h, dim);
        canvas.drawRect(0, box.top, box.left, box.bottom, dim);
        canvas.drawRect(box.right, box.top, w, box.bottom, dim);

        canvas.drawRoundRect(box, dp(14), dp(14), frame);

        float arm = dp(26);
        float r = dp(14);
        // top-left
        canvas.drawLine(box.left, box.top + arm, box.left, box.top + r, bracket);
        canvas.drawLine(box.left, box.top + 1, box.left + r, box.top + 1, bracket);
        // top-right
        canvas.drawLine(box.right, box.top + arm, box.right, box.top + r, bracket);
        canvas.drawLine(box.right, box.top + 1, box.right - r, box.top + 1, bracket);
        // bottom-left
        canvas.drawLine(box.left, box.bottom - arm, box.left, box.bottom - r, bracket);
        canvas.drawLine(box.left, box.bottom - 1, box.left + r, box.bottom - 1, bracket);
        // bottom-right
        canvas.drawLine(box.right, box.bottom - arm, box.right, box.bottom - r, bracket);
        canvas.drawLine(box.right, box.bottom - 1, box.right - r, box.bottom - 1, bracket);

        float y = box.top + dp(8) + (box.height() - dp(16)) * progress;
        line.setAlpha(190);
        canvas.drawLine(box.left + dp(10), y, box.right - dp(10), y, line);

        canvas.drawText("将终端二维码放入框内", w / 2f, box.bottom + dp(28), hint);

        if (isShown()) {
            progress = ((AnimationUtils.currentAnimationTimeMillis() % 2200L) / 2200f);
            progress = (float) new AccelerateDecelerateInterpolator()
                    .getInterpolation(progress < 0.5f ? progress * 2 : (1 - progress) * 2);
            invalidate();
        }
    }
}
