package com.pdzdh.calcutool.comtrade;

import java.util.Locale;

/**
 * 小电流接地故障方向判别。
 *
 * <p>取零序电压、零序电流通道，先找零序电压突变时刻，再看该时刻起一个周波的
 * 瞬时零序功率方向（基波），以及其后 10 个周波的稳态零序电压是否仍然存在，
 * 据此给出界内 / 界外 / 瞬时性的结论。</p>
 */
public final class SmallCurrentDetector {

    public static final class Result {
        public boolean detected = false;
        public int onsetIndex = -1;
        public double onsetTimeMs = 0;

        public double u0FaultRms = 0;
        public double i0FaultRms = 0;
        public double p0 = 0;
        public double phiDeg = 0;
        public double cosPhi = 0;
        /** 参与积分的首半波宽度（ms）。 */
        public double halfCycleMs = 0;

        public double u0SteadyRms = 0;
        public double u0Max = 0;
        public boolean steadyPresent = false;

        public String verdict = "未检测到零序电压突变";
        public String detail = "";
        public int level = 0; // 0 未知/无, 1 界内, -1 界外, 2 瞬时
    }

    private SmallCurrentDetector() {}

    public static Result analyze(ComtradeCase c, int u0Ch, int i0Ch, double f0) {
        Result r = new Result();
        if (c == null || u0Ch < 0 || i0Ch < 0
                || u0Ch >= c.analogCount() || i0Ch >= c.analogCount()
                || c.sampleCount < 16) {
            r.detail = "请先选择零序电压与零序电流通道";
            return r;
        }

        float[] u = c.analog[u0Ch];
        float[] i0 = c.analog[i0Ch];
        double fs = c.sampleRate > 0 ? c.sampleRate : 1000;
        int cyc = WaveMath.cycleSamples(fs, f0);

        double uMax = 0;
        for (int k = 0; k < c.sampleCount; k++) uMax = Math.max(uMax, Math.abs(u[k]));
        r.u0Max = uMax;
        if (uMax <= 1e-9) {
            r.detail = "零序电压通道始终为 0，无法判别";
            return r;
        }

        // 1. 基线（起始一个周波）与突变点
        double base = WaveMath.rms(u, 0, Math.min(cyc, c.sampleCount));
        int onset = -1;
        for (int s = cyc; s + cyc <= c.sampleCount; s += Math.max(1, cyc / 8)) {
            double rms = WaveMath.rms(u, s, s + cyc);
            if (rms > Math.max(base * 4, uMax * 0.15) && rms > 1e-6) {
                onset = s;
                break;
            }
        }
        if (onset < 0) {
            // 全程都有零序电压：以最大斜率处作为突变点
            double best = 0;
            for (int k = 1; k < c.sampleCount; k++) {
                double d = Math.abs(u[k] - u[k - 1]);
                if (d > best) {
                    best = d;
                    onset = k;
                }
            }
            if (best < uMax * 0.02) {
                r.detail = String.format(Locale.US,
                        "零序电压无明显突变（峰值 %.1f）", uMax);
                return r;
            }
        }
        r.detected = true;
        r.onsetIndex = onset;
        r.onsetTimeMs = c.time[onset] * 1000;

        // 2. 突变后“首半波”的瞬时功率积分：P0 = (1/T_half)∫u0·i0 dt
        int half = Math.max(2, cyc / 2);
        int from = Math.max(0, Math.min(onset, c.sampleCount - 1));
        int hTo = Math.min(c.sampleCount, from + half);
        int n = hTo - from;
        double sum = 0;
        for (int k = from; k < hTo; k++) sum += (double) u[k] * i0[k];
        r.p0 = n > 0 ? sum / n : 0;
        r.u0FaultRms = WaveMath.rms(u, from, hTo);
        r.i0FaultRms = WaveMath.rms(i0, from, hTo);
        r.halfCycleMs = n / fs * 1000.0;

        // 基波相位差作为辅助信息（整周波）
        int fTo = Math.min(c.sampleCount, from + cyc);
        double[] tu = WaveMath.tone(u, from, fTo, f0, fs);
        double[] ti = WaveMath.tone(i0, from, fTo, f0, fs);
        r.phiDeg = WaveMath.norm(tu[1] - ti[1]);
        r.cosPhi = Math.cos(Math.toRadians(r.phiDeg));

        // 3. 其后 10 个周波的稳态零序电压
        int steadyFrom = onset + 10 * cyc;
        if (steadyFrom + cyc <= c.sampleCount) {
            r.u0SteadyRms = WaveMath.rms(u, steadyFrom, steadyFrom + cyc);
        } else {
            int tail = Math.max(0, c.sampleCount - cyc);
            r.u0SteadyRms = WaveMath.rms(u, tail, c.sampleCount);
        }
        r.steadyPresent = r.u0SteadyRms > Math.max(0.25 * r.u0FaultRms, uMax * 0.08);

        // 4. 结论
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.US,
                "突变时刻 %.2fms（第 %d 点）\n首半波 %.1fms 积分零序功率 %.3f\n"
                        + "φ=%.1f°，cosφ=%.2f，10 周波后零序电压 %.2f（%s）",
                r.onsetTimeMs, onset + 1, r.halfCycleMs, r.p0,
                r.phiDeg, r.cosPhi, r.u0SteadyRms,
                r.steadyPresent ? "持续存在" : "已衰减"));
        r.detail = sb.toString();

        if (Math.abs(r.cosPhi) < 0.2) {
            r.verdict = "零序功率方向不明确，建议人工复核";
            r.level = 0;
        } else if (!r.steadyPresent) {
            r.verdict = "瞬时性接地（零序电压未持续），暂不判为界内故障";
            r.level = 2;
        } else if (r.p0 < 0) {
            r.verdict = "判定：界内故障（零序功率由线路流向母线）";
            r.level = 1;
        } else {
            r.verdict = "判定：界外故障（零序功率由母线流向线路）";
            r.level = -1;
        }
        return r;
    }
}
