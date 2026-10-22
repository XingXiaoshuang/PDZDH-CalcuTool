package com.pdzdh.calcutool.comtrade;

/** 波形数值分析：真有效值、极值、基波/谐波（单频 DFT + Hann 窗）、频率估算。 */
public final class WaveMath {

    public static final double DEFAULT_F0 = 50.0;

    private WaveMath() {}

    public static double rms(float[] x, int from, int toExclusive) {
        int a = Math.max(0, from), b = Math.min(x.length, toExclusive);
        if (b <= a) return 0;
        double sum = 0;
        for (int i = a; i < b; i++) sum += (double) x[i] * x[i];
        return Math.sqrt(sum / (b - a));
    }

    public static double mean(float[] x, int from, int toExclusive) {
        int a = Math.max(0, from), b = Math.min(x.length, toExclusive);
        if (b <= a) return 0;
        double sum = 0;
        for (int i = a; i < b; i++) sum += x[i];
        return sum / (b - a);
    }

    public static double max(float[] x, int from, int toExclusive) {
        int a = Math.max(0, from), b = Math.min(x.length, toExclusive);
        double m = -Double.MAX_VALUE;
        for (int i = a; i < b; i++) m = Math.max(m, x[i]);
        return b <= a ? 0 : m;
    }

    public static double min(float[] x, int from, int toExclusive) {
        int a = Math.max(0, from), b = Math.min(x.length, toExclusive);
        double m = Double.MAX_VALUE;
        for (int i = a; i < b; i++) m = Math.min(m, x[i]);
        return b <= a ? 0 : m;
    }

    /** 单频 DFT：返回 {有效值, 相位角(度)}，Hann 窗抑制泄漏。 */
    public static double[] tone(float[] x, int from, int toExclusive, double freq, double fs) {
        int a = Math.max(0, from), b = Math.min(x.length, toExclusive);
        int n = b - a;
        if (n <= 1 || fs <= 0) return new double[]{0, 0};
        double re = 0, im = 0, wsum = 0;
        for (int i = 0; i < n; i++) {
            double w = 0.5 - 0.5 * Math.cos(2 * Math.PI * i / (n - 1)); // Hann
            double ang = 2 * Math.PI * freq * i / fs;
            double v = x[a + i] * w;
            re += v * Math.cos(ang);
            im -= v * Math.sin(ang);
            wsum += w;
        }
        if (wsum == 0) return new double[]{0, 0};
        double ampPeak = 2.0 * Math.hypot(re, im) / wsum;
        double phase = Math.toDegrees(Math.atan2(im, re));
        return new double[]{ampPeak / Math.sqrt(2.0), norm(phase)};
    }

    /** 基波 + 1..order 次谐波幅值（有效值）与相位；[0] 为基波。 */
    public static double[][] harmonics(float[] x, int from, int toExclusive,
                                       double fs, double f0, int order) {
        double[][] out = new double[order + 1][];
        out[0] = tone(x, from, toExclusive, 0, fs);   // 直流分量按纯均值处理
        out[0][0] = Math.abs(mean(x, from, toExclusive));
        for (int k = 1; k <= order; k++) {
            out[k] = tone(x, from, toExclusive, k * f0, fs);
        }
        return out;
    }

    /** 总谐波畸变率（%），基于 2..order 次谐波有效值。 */
    public static double thd(double[][] harm, int order) {
        double fund = harm[1][0];
        if (fund <= 0) return 0;
        double sum = 0;
        for (int k = 2; k <= order; k++) sum += harm[k][0] * harm[k][0];
        return Math.sqrt(sum) / fund * 100.0;
    }

    /** 由基波过零点估算实际频率（Hz）。 */
    public static double frequency(float[] x, int from, int toExclusive, double fs, double f0) {
        int a = Math.max(1, from), b = Math.min(x.length, toExclusive);
        double firstT = Double.NaN, lastT = Double.NaN;
        int count = 0;
        for (int i = a; i < b; i++) {
            if (x[i - 1] < 0 && x[i] >= 0) {
                double t = i / fs;
                if (Double.isNaN(firstT)) firstT = t;
                lastT = t;
                count++;
            }
        }
        if (count >= 2 && lastT > firstT) return (count - 1) / (lastT - firstT);
        return f0;
    }

    /** 一个基波周期的采样点数（至少 8）。 */
    public static int cycleSamples(double fs, double f0) {
        if (fs <= 0 || f0 <= 0) return 64;
        return Math.max(8, (int) Math.round(fs / f0));
    }

    public static double norm(double deg) {
        double d = deg % 360.0;
        if (d > 180) d -= 360;
        if (d <= -180) d += 360;
        return d;
    }
}
