package com.pdzdh.calcutool.comtrade;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 把整段录波（全部模拟量通道 + 数字量通道）渲染成一张高清全景图，用于导出分享/存档。
 *
 * <p>不依赖屏幕尺寸与当前缩放：按 2400px 宽离屏绘制，包含厂家/装置信息、采样信息、
 * 每通道上/下峰值、T0/T1/T2 时标与 Δt。</p>
 */
public final class ComtradeExporter {

    private static final int WIDTH = 2400;
    private static final int MARGIN_L = 230;
    private static final int MARGIN_R = 70;
    private static final int HEADER_H = 320;
    private static final int FOOTER_H = 120;
    private static final int LANE_H = 130;
    private static final int DIGITAL_ROW_H = 30;

    private ComtradeExporter() {}

    public static Bitmap render(ComtradeCase c, boolean primary, boolean showDigital,
                                double cursorA, double cursorB, String title) {
        int nA = c.analogCount();
        int nD = showDigital ? c.digitalCount() : 0;
        int digitalH = nD > 0 ? 50 + nD * DIGITAL_ROW_H : 0;
        int height = HEADER_H + nA * LANE_H + digitalH + FOOTER_H;

        Bitmap bmp = Bitmap.createBitmap(WIDTH, height, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);
        cv.drawColor(0xFF0D0C11);

        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        Path path = new Path();
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
        text.setTypeface(Typeface.MONOSPACE);

        float left = MARGIN_L;
        float right = WIDTH - MARGIN_R;
        float plotW = right - left;
        float top = HEADER_H;
        float bottom = height - FOOTER_H;
        double t0 = c.time[0];
        double t1 = c.time[Math.max(0, c.sampleCount - 1)];
        double span = Math.max(1e-9, t1 - t0);

        drawHeader(cv, c, primary, title, fill, text, nA, nD);

        // ── 网格与时间刻度 ──
        line.setStrokeWidth(1);
        line.setColor(0x1AFFFFFF);
        for (int i = 0; i <= 6; i++) {
            float y = top + (bottom - top) * i / 6f;
            cv.drawLine(left, y, right, y, line);
        }
        double step = WaveformView.niceStep(span);
        for (double t = Math.ceil(t0 / step) * step; t <= t1; t += step) {
            float x = (float) (left + (t - t0) / span * plotW);
            line.setColor(Math.abs(t) < step / 2 ? 0x44A387FF : 0x1AFFFFFF);
            cv.drawLine(x, top, x, bottom, line);
        }

        // ── 模拟量通道 ──
        for (int ch = 0; ch < nA; ch++) {
            float laneTop = top + ch * (float) LANE_H;
            float cy = laneTop + LANE_H / 2f;
            float halfH = LANE_H / 2f * 0.82f;
            ComtradeCase.AnalogChannel ac = c.analogs.get(ch);
            float[] v = c.analog[ch];
            double factor = primary ? 1 : c.secondaryRatio(ch);

            double peak = 0, mn = Double.MAX_VALUE, mx = -Double.MAX_VALUE;
            for (int i = 0; i < c.sampleCount; i++) {
                double d = v[i] * factor;
                peak = Math.max(peak, Math.abs(d));
                mn = Math.min(mn, d);
                mx = Math.max(mx, d);
            }
            if (peak < 1e-9) peak = 1;

            line.setColor(0x22FFFFFF);
            line.setStrokeWidth(1);
            cv.drawLine(left, cy, right, cy, line);

            int color = PhaseColors.of(ac, ch);
            line.setColor(color);
            line.setStrokeWidth(2.2f);
            path.reset();
            float pxPerSample = (float) (plotW / Math.max(1, c.sampleCount));
            if (pxPerSample >= 1f) {
                for (int i = 0; i < c.sampleCount; i++) {
                    float x = (float) (left + (c.time[i] - t0) / span * plotW);
                    float y = (float) (cy - v[i] * factor / peak * halfH);
                    if (i == 0) path.moveTo(x, y);
                    else path.lineTo(x, y);
                }
                cv.drawPath(path, line);
            } else {
                int cols = (int) plotW;
                for (int col = 0; col < cols; col++) {
                    int s0 = (int) ((long) col * c.sampleCount / cols);
                    int s1 = (int) ((long) (col + 1) * c.sampleCount / cols);
                    if (s1 <= s0) s1 = s0 + 1;
                    double lmin = Double.MAX_VALUE, lmax = -Double.MAX_VALUE;
                    for (int i = s0; i < Math.min(c.sampleCount, s1); i++) {
                        double d = v[i] * factor;
                        lmin = Math.min(lmin, d);
                        lmax = Math.max(lmax, d);
                    }
                    if (lmin > lmax) continue;
                    float x = left + col;
                    cv.drawLine(x, (float) (cy - lmax / peak * halfH), x,
                            (float) Math.max(cy - lmax / peak * halfH + 1, cy - lmin / peak * halfH),
                            line);
                }
            }

            // 左侧：上峰值 / 通道名 / 下峰值
            String unit = ac.unit == null ? "" : ac.unit;
            text.setTextAlign(Paint.Align.RIGHT);
            text.setTypeface(Typeface.MONOSPACE);
            text.setTextSize(19);
            text.setColor(0xD9FF8A80);
            cv.drawText(String.format(Locale.US, "%s%s", fmt(mx), unit), left - 14, cy - 22, text);
            text.setTextSize(25);
            text.setColor(color);
            cv.drawText(ac.name, left - 14, cy + 4, text);
            text.setTextSize(19);
            text.setColor(0xD964D2FF);
            cv.drawText(String.format(Locale.US, "%s%s", fmt(mn), unit), left - 14, cy + 30, text);
        }

        // ── 数字量通道 ──
        if (nD > 0) {
            float dTop = top + nA * (float) LANE_H;
            line.setColor(0x33FFFFFF);
            line.setStrokeWidth(1.5f);
            cv.drawLine(left, dTop, right, dTop, line);
            text.setTextAlign(Paint.Align.LEFT);
            text.setTextSize(20);
            text.setColor(0xCC53DAAF);
            cv.drawText("数字量通道", 60, dTop + 26, text);

            float rowsTop = dTop + 40;
            for (int ch = 0; ch < nD; ch++) {
                float y1 = rowsTop + ch * (float) DIGITAL_ROW_H + DIGITAL_ROW_H - 5;
                float y0 = rowsTop + ch * (float) DIGITAL_ROW_H + 5;
                boolean prev = c.digitalAt(ch, 0);
                float prevX = left;
                line.setColor(0xCC53DAAF);
                line.setStrokeWidth(2f);
                for (int i = 1; i < c.sampleCount; i++) {
                    boolean cur = c.digitalAt(ch, i);
                    if (cur != prev) {
                        float x = (float) (left + (c.time[i] - t0) / span * plotW);
                        cv.drawLine(prevX, prev ? y0 : y1, x, prev ? y0 : y1, line);
                        cv.drawLine(x, y0, x, y1, line);
                        prevX = x;
                        prev = cur;
                    }
                }
                cv.drawLine(prevX, prev ? y0 : y1, right, prev ? y0 : y1, line);

                text.setTextAlign(Paint.Align.RIGHT);
                text.setTextSize(17);
                text.setColor(0xCC53DAAF);
                cv.drawText(c.digitals.get(ch).name, left - 14,
                        (y0 + y1) / 2f + 6, text);
            }
        }

        // ── 时标与游标 ──
        drawCursor(cv, left, right, top, bottom, t0, t1, span, cursorA,
                0xFFB79BFF, "T1", text, line);
        drawCursor(cv, left, right, top, bottom, t0, t1, span, cursorB,
                0xFFFFA65C, "T2", text, line);

        line.setStrokeWidth(2f);
        line.setColor(0xB364D2FF);
        line.setPathEffect(new DashPathEffect(new float[]{12, 8}, 0));
        float x0 = (float) (left + (0 - t0) / span * plotW);
        if (x0 >= left && x0 <= right) cv.drawLine(x0, top - 34, x0, bottom, line);
        line.setPathEffect(null);
        text.setTextAlign(Paint.Align.LEFT);
        text.setTextSize(20);
        text.setColor(0xFF64D2FF);
        cv.drawText("T0", x0 + 6, top - 14, text);

        text.setTextAlign(Paint.Align.RIGHT);
        text.setTextSize(22);
        text.setColor(0xFFFFFFFF);
        cv.drawText(String.format(Locale.US, "Δt %.2f ms", Math.abs(cursorB - cursorA) * 1000),
                right, top - 14, text);

        // 时间轴标签
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(20);
        text.setColor(0xCC948EAC);
        for (double t = Math.ceil(t0 / step) * step; t <= t1; t += step) {
            float x = (float) (left + (t - t0) / span * plotW);
            cv.drawText(msLabel(t), x, bottom + 34, text);
        }
        text.setTextAlign(Paint.Align.LEFT);
        text.setTextSize(19);
        text.setColor(0x99948EAC);
        cv.drawText("时间轴单位 ms（0 为录波触发时刻）", left, height - 28, text);
        text.setTextAlign(Paint.Align.RIGHT);
        cv.drawText("落霞孤鴻 · 配电工具箱", right, height - 28, text);
        return bmp;
    }

    private static void drawCursor(Canvas cv, float left, float right, float top, float bottom,
                                   double t0, double t1, double span, double t,
                                   int color, String label, Paint text, Paint line) {
        if (t < t0 || t > t1) return;
        float x = (float) (left + (t - t0) / span * (right - left));
        line.setPathEffect(null);
        line.setStrokeWidth(2.5f);
        line.setColor(color);
        cv.drawLine(x, top - 34, x, bottom, line);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(22);
        text.setColor(color);
        // 靠边的游标标签往内收，避免被裁掉或与 Δt 重叠
        float labelX = Math.max(left + 26, Math.min(right - 120, x));
        cv.drawText(label, labelX, top - 14, text);
    }

    private static void drawHeader(Canvas cv, ComtradeCase c, boolean primary, String title,
                                   Paint fill, Paint text, int nA, int nD) {
        text.setTextAlign(Paint.Align.LEFT);
        text.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        text.setTextSize(46);
        text.setColor(0xFFECE9F5);
        cv.drawText(title, 60, 78, text);

        text.setTypeface(Typeface.MONOSPACE);
        text.setTextSize(26);
        text.setColor(0xCC948EAC);
        cv.drawText(String.format(Locale.US, "%s   %s   格式 %s   数据 %s",
                c.station, c.device, c.revision, typeName(c.dataType)), 60, 130, text);

        text.setTextSize(24);
        text.setColor(0xCCECE9F5);
        String info = String.format(Locale.US,
                "采样率 %.0fHz    采样点 %d    时长 %.3fms    触发点 第%d点    "
                        + "模拟量 %d / 数字量 %d    %s",
                c.sampleRate, c.sampleCount, c.duration() * 1000, c.triggerIndex + 1,
                nA, nD, primary ? "一次值" : "二次值");
        cv.drawText(info, 60, 186, text);

        text.setTextSize(22);
        text.setColor(0x99948EAC);
        cv.drawText(String.format(Locale.US, "起始 %s    触发 %s    %s",
                c.startTime, c.triggerTime,
                new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date())),
                60, 232, text);

        fill.setColor(0x33A387FF);
        cv.drawRect(60, 258, WIDTH - 60, 261, fill);
    }

    private static String typeName(int type) {
        switch (type) {
            case ComtradeCase.TYPE_ASCII: return "ASCII";
            case ComtradeCase.TYPE_BINARY32: return "BINARY32";
            case ComtradeCase.TYPE_FLOAT32: return "FLOAT32";
            default: return "BINARY";
        }
    }

    private static String msLabel(double t) {
        double ms = t * 1000;
        if (Math.abs(ms) < 0.05) return "0";
        return String.format(Locale.US, "%.0f", ms);
    }

    private static String fmt(double v) {
        double a = Math.abs(v);
        if (a >= 10000) return String.format(Locale.US, "%.0f", v);
        if (a >= 100) return String.format(Locale.US, "%.1f", v);
        if (a >= 10) return String.format(Locale.US, "%.2f", v);
        if (a >= 0.1) return String.format(Locale.US, "%.3f", v);
        return String.format(Locale.US, "%.4f", v);
    }
}
