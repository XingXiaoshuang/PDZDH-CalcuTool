package com.pdzdh.calcutool.comtrade;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import java.util.Locale;

/**
 * 录波波形视图。
 *
 * <p>分道显示模拟量与数字量，支持时间缩放/平移、通道上下滚动、横纵滚动条、双游标、
 * 峰值与峰峰值显示、一次值/二次值切换，以及“填充/保持比例”两种描绘方式。</p>
 */
public class WaveformView extends View {

    public interface Listener {
        void onSelect(int analogIndex);

        void onCursorsChanged();

        void onViewChanged();

        void onCursorDragEnd();
    }

    private ComtradeCase data;
    private boolean[] visible;
    private boolean showDigital = false;
    private int selected = 0;

    private double tStart, tEnd;
    private double cursorA, cursorB;
    private boolean showT0 = true;

    /** 是否按一次值显示。 */
    private boolean primary = true;
    /** true：通道固定高度，超出可上下滚动；false：压缩塞满绘制区。 */
    private boolean proportional = true;

    private float scrollY = 0;
    private float contentH = 0;

    private Listener listener;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint scrollPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF laneRect = new RectF();
    private final float density;

    private static final int DRAG_NONE = 0, DRAG_A = 1, DRAG_B = 2, DRAG_PAN = 3,
            DRAG_VSCROLL = 4, DRAG_HSCROLL = 5;
    private int dragging = DRAG_NONE;
    private float lastX, lastY, downX, downY;
    private boolean moved;
    private long downTime;
    private final ScaleGestureDetector scaleDetector;

    private static final float LANE_H_DP = 56f;
    private static final float DIGITAL_ROW_DP = 17f;

    public WaveformView(Context c) {
        super(c);
        density = c.getResources().getDisplayMetrics().density;
        text.setTypeface(Typeface.MONOSPACE);
        grid.setStrokeWidth(dp(0.8f));
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(dp(1.1f));
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
        scaleDetector = new ScaleGestureDetector(c,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(ScaleGestureDetector d) {
                        if (data == null) return false;
                        double span = tEnd - tStart;
                        double focus = tStart + span
                                * ((d.getFocusX() - plotLeft()) / Math.max(1, plotWidth()));
                        double next = clampSpan(span / d.getScaleFactor());
                        double ratio = (focus - tStart) / span;
                        tStart = focus - next * ratio;
                        tEnd = tStart + next;
                        clampWindow();
                        invalidate();
                        if (listener != null) listener.onViewChanged();
                        return true;
                    }
                });
    }

    public void setListener(Listener l) { this.listener = l; }

    public void setData(ComtradeCase c) {
        data = c;
        int n = c.analogCount();
        visible = new boolean[n];
        for (int i = 0; i < n; i++) visible[i] = i < 6;
        selected = 0;
        scrollY = 0;
        tStart = c.time[0];
        tEnd = c.time[Math.max(0, c.sampleCount - 1)];
        cursorA = c.time[0];
        cursorB = c.time[Math.max(0, c.sampleCount - 1)];
        invalidate();
    }

    public ComtradeCase data() { return data; }

    public void setVisible(int ch, boolean on) {
        if (visible == null || ch >= visible.length) return;
        visible[ch] = on;
        invalidate();
    }

    public boolean isVisible(int ch) {
        return visible != null && ch < visible.length && visible[ch];
    }

    public void setShowDigital(boolean on) {
        showDigital = on;
        scrollY = 0;
        invalidate();
    }

    public boolean showDigital() { return showDigital; }

    public int selected() { return selected; }

    public double cursorA() { return cursorA; }

    public double cursorB() { return cursorB; }

    public void setCursors(double a, double b) {
        cursorA = a;
        cursorB = b;
        invalidate();
    }

    public void setShowT0(boolean on) {
        showT0 = on;
        invalidate();
    }

    public boolean showT0() { return showT0; }

    public void setPrimary(boolean on) {
        primary = on;
        invalidate();
    }

    public boolean isPrimary() { return primary; }

    public void setProportional(boolean on) {
        proportional = on;
        scrollY = 0;
        invalidate();
    }

    public boolean isProportional() { return proportional; }

    /** 显示值：一次值或二次值。 */
    public double display(int ch, double raw) {
        if (data == null || primary) return raw;
        return raw * data.secondaryRatio(ch);
    }

    public void reset() {
        if (data == null) return;
        tStart = data.time[0];
        tEnd = data.time[Math.max(0, data.sampleCount - 1)];
        cursorA = tStart;
        cursorB = tEnd;
        scrollY = 0;
        invalidate();
        if (listener != null) listener.onViewChanged();
    }

    public void zoom(double factor) {
        if (data == null) return;
        double span = tEnd - tStart;
        double mid = (tStart + tEnd) / 2;
        double next = clampSpan(span * factor);
        tStart = mid - next / 2;
        tEnd = mid + next / 2;
        clampWindow();
        invalidate();
        if (listener != null) listener.onViewChanged();
    }

    private double clampSpan(double span) {
        if (data == null) return span;
        double minSpan = Math.max(1e-5, 4.0 / Math.max(1, data.sampleRate));
        double maxSpan = Math.max(1e-3, data.duration());
        return Math.min(maxSpan, Math.max(minSpan, span));
    }

    private void clampWindow() {
        if (data == null) return;
        double lo = data.time[0], hi = data.time[Math.max(0, data.sampleCount - 1)];
        double span = tEnd - tStart;
        if (tStart < lo) {
            tStart = lo;
            tEnd = tStart + span;
        }
        if (tEnd > hi) {
            tEnd = hi;
            tStart = Math.max(lo, tEnd - span);
        }
    }

    private float dp(float v) { return v * density; }

    private float plotLeft() { return dp(64); }

    private float plotRight() { return getWidth() - dp(62); }

    private float plotWidth() { return Math.max(1, plotRight() - plotLeft()); }

    private float plotTop() { return dp(28); }

    private float hScrollTop() { return getHeight() - dp(26); }

    private float plotBottom() { return hScrollTop() - dp(4); }

    private float plotHeight() { return Math.max(1, plotBottom() - plotTop()); }

    private float xOf(double t) {
        if (tEnd <= tStart) return plotLeft();
        return (float) (plotLeft() + (t - tStart) / (tEnd - tStart) * plotWidth());
    }

    private double tOf(float x) {
        return tStart + (x - plotLeft()) / plotWidth() * (tEnd - tStart);
    }

    private int visibleCount() {
        int n = 0;
        if (visible == null) return 0;
        for (boolean b : visible) if (b) n++;
        return n;
    }

    private float laneHeight() {
        int n = visibleCount();
        if (n == 0) return 0;
        if (proportional) return dp(LANE_H_DP);
        return Math.max(dp(26), (plotHeight() - digitalHeight()) / n);
    }

    private float digitalHeight() {
        if (!showDigital || data == null || data.digitalCount() == 0) return 0;
        int n = data.digitalCount();
        if (proportional) return dp(20) + n * dp(DIGITAL_ROW_DP);
        return Math.min(plotHeight() * 0.34f, Math.max(dp(26), n * dp(9)));
    }

    private void computeContentHeight() {
        contentH = visibleCount() * laneHeight() + digitalHeight();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (data == null) return;

        computeContentHeight();
        float maxScroll = Math.max(0, contentH - plotHeight());
        if (scrollY > maxScroll) scrollY = maxScroll;
        if (scrollY < 0) scrollY = 0;

        drawTimeGrid(canvas);

        // 波形只在绘制区内绘制，滚动时不会溢出到时间轴/滚动条上
        canvas.save();
        canvas.clipRect(0, plotTop() - dp(2), getWidth(), plotBottom() + dp(2));

        float laneH = laneHeight();
        int laneIndex = 0;
        for (int ch = 0; ch < data.analogCount(); ch++) {
            if (!visible[ch]) continue;
            float y0 = plotTop() - scrollY + laneH * laneIndex;
            float y1 = y0 + laneH;
            if (y1 > plotTop() - 2 && y0 < plotBottom() + 2) {
                drawAnalogLane(canvas, ch, y0, y1);
            }
            laneIndex++;
        }
        if (digitalHeight() > 0) {
            float top = plotTop() - scrollY + laneH * visibleCount();
            drawDigitalLanes(canvas, top, top + digitalHeight());
        }
        canvas.restore();

        drawCursors(canvas);
        drawTimeLabels(canvas);
        drawScrollbars(canvas, maxScroll);
    }

    private void drawTimeGrid(Canvas canvas) {
        float left = plotLeft(), right = plotRight(), top = plotTop(), bottom = plotBottom();
        grid.setColor(0x14FFFFFF);
        for (int i = 0; i <= 4; i++) {
            float y = top + (bottom - top) * i / 4f;
            canvas.drawLine(left, y, right, y, grid);
        }
        double step = niceStep(tEnd - tStart);
        double t = Math.ceil(tStart / step) * step;
        for (; t <= tEnd; t += step) {
            float x = xOf(t);
            grid.setColor(Math.abs(t) < step / 2 ? 0x33A387FF : 0x14FFFFFF);
            canvas.drawLine(x, top, x, bottom, grid);
        }
    }

    private void drawAnalogLane(Canvas canvas, int ch, float y0, float y1) {
        float left = plotLeft(), right = plotRight();
        laneRect.set(left, y0, right, y1);
        float cy = (y0 + y1) / 2f;
        float halfH = (y1 - y0) / 2f * 0.84f;

        ComtradeCase.AnalogChannel chInfo = data.analogs.get(ch);
        float[] v = data.analog[ch];
        double factor = primary ? 1 : data.secondaryRatio(ch);

        int i0 = Math.max(0, data.indexOfTime(tStart) - 1);
        int i1 = Math.min(data.sampleCount - 1, data.indexOfTime(tEnd) + 1);
        double peak = 0, mn = Double.MAX_VALUE, mx = -Double.MAX_VALUE;
        for (int i = i0; i <= i1; i++) {
            double d = v[i] * factor;
            peak = Math.max(peak, Math.abs(d));
            mn = Math.min(mn, d);
            mx = Math.max(mx, d);
        }
        if (peak < 1e-9) peak = 1;

        if (ch == selected) {
            fill.setColor(0x0FA387FF);
            canvas.drawRect(laneRect, fill);
            fill.setColor(0xFFA387FF);
            canvas.drawRect(left, y0, left + dp(2), y1, fill);
        }

        line.setColor(0x26FFFFFF);
        line.setStrokeWidth(dp(0.8f));
        canvas.drawLine(left, cy, right, cy, line);

        line.setColor(colorOf(ch));
        line.setStrokeWidth(ch == selected ? dp(1.5f) : dp(1.1f));
        path.reset();
        float pxPerSample = (float) (plotWidth() / Math.max(1, (tEnd - tStart) * data.sampleRate));
        if (pxPerSample >= 1.2f) {
            boolean started = false;
            for (int i = i0; i <= i1; i++) {
                float x = xOf(data.time[i]);
                float y = (float) (cy - v[i] * factor / peak * halfH);
                if (!started) {
                    path.moveTo(x, y);
                    started = true;
                } else {
                    path.lineTo(x, y);
                }
            }
            canvas.drawPath(path, line);
        } else {
            int cols = (int) plotWidth();
            for (int c = 0; c < cols; c++) {
                int s0 = data.indexOfTime(tOf(left + c));
                int s1 = data.indexOfTime(tOf(left + c + 1));
                if (s1 <= s0) s1 = s0 + 1;
                double lmin = Double.MAX_VALUE, lmax = -Double.MAX_VALUE;
                for (int i = Math.max(0, s0); i < Math.min(data.sampleCount, s1); i++) {
                    double d = v[i] * factor;
                    lmin = Math.min(lmin, d);
                    lmax = Math.max(lmax, d);
                }
                if (lmin > lmax) continue;
                float yTop = (float) (cy - lmax / peak * halfH);
                float yBot = (float) (cy - lmin / peak * halfH);
                canvas.drawLine(left + c, yTop, left + c, Math.max(yTop + dp(0.8f), yBot), line);
            }
        }

        // 左侧：上峰值 / 通道名 / 下峰值
        String unit = chInfo.unit == null ? "" : chInfo.unit;
        text.setTextAlign(Paint.Align.RIGHT);
        text.setTextSize(dp(7.4f));
        text.setTypeface(Typeface.MONOSPACE);
        text.setColor(0xB2FF8A80);
        canvas.drawText(String.format(Locale.US, "%s%s", fmt(mx), unit), left - dp(6),
                cy - dp(13), text);

        text.setTextSize(dp(9.2f));
        text.setTypeface(Typeface.create(Typeface.MONOSPACE,
                ch == selected ? Typeface.BOLD : Typeface.NORMAL));
        text.setColor(ch == selected ? 0xFFECE9F5 : colorOf(ch));
        canvas.drawText(clip(chInfo.name, 8), left - dp(6), cy - dp(1), text);

        text.setTextSize(dp(7.4f));
        text.setTypeface(Typeface.MONOSPACE);
        text.setColor(0xB264D2FF);
        canvas.drawText(String.format(Locale.US, "%s%s", fmt(mn), unit), left - dp(6),
                cy + dp(11), text);

        // 右侧：T1 / T2 瞬时值
        text.setTypeface(Typeface.MONOSPACE);
        text.setTextSize(dp(8));
        text.setTextAlign(Paint.Align.LEFT);
        int ia = Math.min(data.sampleCount - 1, data.indexOfTime(cursorA));
        int ib = Math.min(data.sampleCount - 1, data.indexOfTime(cursorB));
        text.setColor(0xFFB79BFF);
        canvas.drawText(fmt(v[ia] * factor), right + dp(4), cy - dp(3), text);
        text.setColor(0xFFFFA65C);
        canvas.drawText(fmt(v[ib] * factor), right + dp(4), cy + dp(9), text);
    }

    private void drawDigitalLanes(Canvas canvas, float top, float bottom) {
        float left = plotLeft(), right = plotRight();
        line.setColor(0x22FFFFFF);
        line.setStrokeWidth(dp(0.8f));
        canvas.drawLine(left, top, right, top, line);

        text.setTextAlign(Paint.Align.LEFT);
        text.setTextSize(dp(7.6f));
        text.setColor(0x8A53DAAF);
        canvas.drawText("数字量", dp(4), top + dp(12), text);

        int n = data.digitalCount();
        float rowsTop = top + dp(16);
        float rowH = (bottom - rowsTop) / Math.max(1, n);
        text.setTextSize(dp(7.2f));
        text.setTextAlign(Paint.Align.RIGHT);
        for (int ch = 0; ch < n; ch++) {
            float y1 = rowsTop + rowH * (ch + 1) - dp(1.5f);
            float y0 = rowsTop + rowH * ch + dp(1.5f);
            boolean prev = data.digitalAt(ch, 0);
            float prevX = left;
            line.setColor(0xB253DAAF);
            line.setStrokeWidth(dp(1.1f));
            for (int i = 1; i < data.sampleCount; i++) {
                boolean cur = data.digitalAt(ch, i);
                if (cur != prev) {
                    float x = xOf(data.time[i]);
                    if (x < left) {
                        prev = cur;
                        prevX = left;
                        continue;
                    }
                    if (x > right) break;
                    canvas.drawLine(prevX, prev ? y0 : y1, x, prev ? y0 : y1, line);
                    canvas.drawLine(x, y0, x, y1, line);
                    prevX = x;
                    prev = cur;
                }
            }
            canvas.drawLine(prevX, prev ? y0 : y1, right, prev ? y0 : y1, line);

            text.setColor(0x9A53DAAF);
            canvas.drawText(clip(data.digitals.get(ch).name, 6), left - dp(6),
                    (y0 + y1) / 2f + dp(2.5f), text);
        }
    }

    private void drawCursors(Canvas canvas) {
        float top = plotTop() - dp(13), bottom = plotBottom() + dp(2);

        if (showT0) {
            float x = xOf(0);
            if (x >= plotLeft() - 1 && x <= plotRight() + 1) {
                line.setColor(0xB364D2FF);
                line.setStrokeWidth(dp(1.1f));
                line.setPathEffect(new DashPathEffect(new float[]{dp(4), dp(3)}, 0));
                canvas.drawLine(x, top, x, bottom, line);
                line.setPathEffect(null);
                text.setTextSize(dp(7.6f));
                text.setColor(0xFF64D2FF);
                text.setTextAlign(Paint.Align.LEFT);
                canvas.drawText("T0", x + dp(2), top + dp(8), text);
            }
        }
        drawCursor(canvas, cursorA, 0xFFB79BFF, "T1", top, bottom);
        drawCursor(canvas, cursorB, 0xFFFFA65C, "T2", top, bottom);

        // 右上方显示 Δt：与右侧 T1/T2 瞬时值一起随拖动更新
        double dtMs = Math.abs(cursorB - cursorA) * 1000;
        text.setTextSize(dp(8));
        text.setColor(0xCCECE9F5);
        text.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(String.format(Locale.US, "Δt %.2fms", dtMs),
                getWidth() - dp(4), top + dp(21), text);
    }

    private void drawCursor(Canvas canvas, double t, int color, String label,
                            float top, float bottom) {
        float x = xOf(t);
        if (x < plotLeft() - 1 || x > plotRight() + 1) return;
        line.setColor(color);
        line.setStrokeWidth(dp(1.2f));
        canvas.drawLine(x, top, x, bottom, line);
        text.setTextSize(dp(8));
        text.setColor(color);
        text.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(label, x, top + dp(8), text);

        fill.setColor(color);
        path.reset();
        path.moveTo(x, top + dp(10));
        path.lineTo(x - dp(4), top + dp(15));
        path.lineTo(x + dp(4), top + dp(15));
        path.close();
        canvas.drawPath(path, fill);
    }

    private void drawTimeLabels(Canvas canvas) {
        text.setTextSize(dp(8));
        text.setColor(0x8A948EAC);
        text.setTextAlign(Paint.Align.CENTER);
        double step = niceStep(tEnd - tStart);
        double t = Math.ceil(tStart / step) * step;
        float y = getHeight() - dp(15);
        for (; t <= tEnd; t += step) {
            float x = xOf(t);
            if (x < plotLeft() || x > plotRight()) continue;
            canvas.drawText(timeLabel(t), x, y, text);
        }
    }

    /** 横纵滚动条：横向为时间窗口，纵向为通道滚动。 */
    private void drawScrollbars(Canvas canvas, float maxScroll) {
        float trackH = dp(3.5f);
        float y = hScrollTop();
        scrollPaint.setColor(0x18FFFFFF);
        canvas.drawRoundRect(plotLeft(), y, plotRight(), y + trackH, trackH / 2, trackH / 2,
                scrollPaint);
        double span = data.duration();
        if (span > 0) {
            float w = plotWidth();
            float tx = (float) (plotLeft() + (tStart - data.time[0]) / span * w);
            float tw = (float) Math.max(dp(24), (tEnd - tStart) / span * w);
            tw = Math.min(tw, plotRight() - tx);
            scrollPaint.setColor(0x99A387FF);
            canvas.drawRoundRect(tx, y, tx + tw, y + trackH, trackH / 2, trackH / 2, scrollPaint);
        }

        if (maxScroll > 0) {
            float x = getWidth() - dp(7);
            float top = plotTop(), bottom = plotBottom();
            scrollPaint.setColor(0x18FFFFFF);
            canvas.drawRoundRect(x, top, x + trackH, bottom, trackH / 2, trackH / 2, scrollPaint);
            float thumbH = Math.max(dp(28), (bottom - top) * (plotHeight() / contentH));
            float thumbY = top + (bottom - top - thumbH) * (scrollY / maxScroll);
            scrollPaint.setColor(0x99A387FF);
            canvas.drawRoundRect(x, thumbY, x + trackH, thumbY + thumbH, trackH / 2, trackH / 2,
                    scrollPaint);
        }
    }

    private String timeLabel(double t) {
        double ms = t * 1000;
        if (Math.abs(ms) < 0.05) return "0";
        if (Math.abs(ms) >= 1000) return String.format(Locale.US, "%.2fs", t);
        return String.format(Locale.US, "%.0f", ms);
    }

    static double niceStep(double span) {
        double raw = span / 6.0;
        double mag = Math.pow(10, Math.floor(Math.log10(Math.max(raw, 1e-9))));
        double n = raw / mag;
        double step;
        if (n <= 1) step = 1;
        else if (n <= 2) step = 2;
        else if (n <= 5) step = 5;
        else step = 10;
        return step * mag;
    }

    private static String clip(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static String fmt(double v) {
        double a = Math.abs(v);
        if (a >= 10000) return String.format(Locale.US, "%.0f", v);
        if (a >= 100) return String.format(Locale.US, "%.1f", v);
        if (a >= 10) return String.format(Locale.US, "%.2f", v);
        if (a >= 0.1) return String.format(Locale.US, "%.3f", v);
        return String.format(Locale.US, "%.4f", v);
    }

    private int colorOf(int ch) {
        return PhaseColors.of(data.analogs.get(ch), ch);
    }

    // ── 手势 ─────────────────────────────────────────────────────────────────

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (data == null) return false;
        scaleDetector.onTouchEvent(e);

        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastX = downX = e.getX();
                lastY = downY = e.getY();
                moved = false;
                downTime = System.currentTimeMillis();
                dragging = hitTest(e.getX(), e.getY());
                if (dragging == DRAG_HSCROLL) seekTo(e.getX());
                return true;
            case MotionEvent.ACTION_MOVE:
                if (Math.abs(e.getX() - downX) > dp(3) || Math.abs(e.getY() - downY) > dp(3)) {
                    moved = true;
                }
                if (dragging == DRAG_A || dragging == DRAG_B) {
                    double t = clampTime(tOf(e.getX()));
                    if (dragging == DRAG_A) cursorA = t; else cursorB = t;
                    invalidate();
                    if (listener != null) listener.onCursorsChanged();
                } else if (dragging == DRAG_HSCROLL) {
                    seekTo(e.getX());
                } else if (dragging == DRAG_VSCROLL) {
                    float maxScroll = Math.max(1, contentH - plotHeight());
                    scrollY -= (e.getY() - lastY) / plotHeight() * maxScroll;
                    if (scrollY < 0) scrollY = 0;
                    if (scrollY > maxScroll) scrollY = maxScroll;
                    invalidate();
                } else if (dragging == DRAG_PAN && moved) {
                    if (Math.abs(e.getX() - downX) > Math.abs(e.getY() - downY)) {
                        double dt = (e.getX() - lastX) / plotWidth() * (tEnd - tStart);
                        tStart -= dt;
                        tEnd -= dt;
                        clampWindow();
                    } else if (contentH > plotHeight()) {
                        float maxScroll = Math.max(1, contentH - plotHeight());
                        scrollY -= (e.getY() - lastY) / plotHeight() * maxScroll;
                        if (scrollY < 0) scrollY = 0;
                        if (scrollY > maxScroll) scrollY = maxScroll;
                    }
                    invalidate();
                    if (listener != null) listener.onViewChanged();
                }
                lastX = e.getX();
                lastY = e.getY();
                return true;
            case MotionEvent.ACTION_UP:
                if (!moved && System.currentTimeMillis() - downTime < 260) {
                    selectLaneAt(e.getY());
                    performClick();
                }
                if (dragging == DRAG_A || dragging == DRAG_B) {
                    if (listener != null) listener.onCursorDragEnd();
                }
                dragging = DRAG_NONE;
                return true;
            case MotionEvent.ACTION_CANCEL:
                dragging = DRAG_NONE;
                return true;
        }
        return super.onTouchEvent(e);
    }

    private void seekTo(float x) {
        double span = tEnd - tStart;
        double total = data.duration();
        double center = data.time[0] + (x - plotLeft()) / plotWidth() * total;
        tStart = center - span / 2;
        tEnd = tStart + span;
        clampWindow();
        invalidate();
        if (listener != null) listener.onViewChanged();
    }

    private int hitTest(float x, float y) {
        if (y >= hScrollTop() - dp(6)) return DRAG_HSCROLL;
        if (contentH > plotHeight() && x >= getWidth() - dp(16)) return DRAG_VSCROLL;
        float xa = xOf(cursorA), xb = xOf(cursorB);
        if (Math.abs(x - xa) <= dp(22)) return DRAG_A;
        if (Math.abs(x - xb) <= dp(22)) return DRAG_B;
        return DRAG_PAN;
    }

    private double clampTime(double t) {
        double lo = data.time[0], hi = data.time[Math.max(0, data.sampleCount - 1)];
        return Math.max(lo, Math.min(hi, t));
    }

    private void selectLaneAt(float y) {
        int n = visibleCount();
        if (n == 0) return;
        float laneH = laneHeight();
        float contentY = y - plotTop() + scrollY;
        if (contentY < 0) return;
        int lane = (int) (contentY / laneH);
        if (lane < 0 || lane >= n) return;
        int idx = -1;
        for (int ch = 0; ch < data.analogCount(); ch++) {
            if (!visible[ch]) continue;
            idx++;
            if (idx == lane) {
                selected = ch;
                invalidate();
                if (listener != null) listener.onSelect(ch);
                return;
            }
        }
    }
}
