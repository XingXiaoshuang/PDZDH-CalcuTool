package com.pdzdh.calcutool.comtrade;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.View;

import java.util.Locale;

/** 谐波柱状图：1~N 次谐波含量百分比。 */
public class SpectrumView extends View {

    private double[] percent = new double[0];
    private double thd;
    private String unit = "";
    private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axis = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;

    public SpectrumView(Context c) {
        super(c);
        density = c.getResources().getDisplayMetrics().density;
        text.setTypeface(Typeface.MONOSPACE);
        text.setTextAlign(Paint.Align.CENTER);
        axis.setColor(0x22FFFFFF);
        axis.setStrokeWidth(dp(1));
    }

    private float dp(float v) { return v * density; }

    public void setData(double[] percent, double thd, String unit) {
        this.percent = percent == null ? new double[0] : percent;
        this.thd = thd;
        this.unit = unit == null ? "" : unit;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int n = percent.length;
        if (n == 0) return;

        float left = dp(34), right = getWidth() - dp(8);
        float top = dp(10), bottom = getHeight() - dp(16);
        float h = bottom - top;

        double max = 1;
        for (int i = 2; i < n; i++) max = Math.max(max, percent[i]);
        max = Math.ceil(max);

        text.setTextSize(dp(8));
        text.setColor(0x8A948EAC);
        text.setTextAlign(Paint.Align.RIGHT);
        for (int i = 0; i <= 4; i++) {
            float y = bottom - h * i / 4f;
            canvas.drawLine(left, y, right, y, axis);
            canvas.drawText(String.format(Locale.US, "%.0f", max * i / 4f),
                    left - dp(4), y + dp(3), text);
        }

        float slot = (right - left) / Math.max(1, n - 1);
        float barW = Math.max(dp(2.5f), slot * 0.62f);
        for (int k = 2; k < n; k++) {
            float x = left + slot * (k - 2) + slot / 2f;
            float bh = (float) (h * Math.min(1, percent[k] / max));
            bar.setColor(0xFF53DAAF);
            canvas.drawRoundRect(x - barW / 2, bottom - bh, x + barW / 2, bottom,
                    dp(1.5f), dp(1.5f), bar);
            text.setColor(0x8A948EAC);
            text.setTextSize(dp(7));
            canvas.drawText(String.valueOf(k), x, bottom + dp(9), text);
        }
        text.setTextAlign(Paint.Align.LEFT);
        text.setTextSize(dp(8));
        text.setColor(0xFF53DAAF);
        canvas.drawText(String.format(Locale.US, "THD %.2f%%", thd), left, top + dp(8), text);
        canvas.drawText(unit, right - text.measureText(unit), top + dp(8), text);
    }
}
