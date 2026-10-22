package com.pdzdh.calcutool.comtrade;

/** 相别配色：A 黄 / B 绿 / C 红，其它（零序等）沿用通道序号颜色。 */
public final class PhaseColors {

    public static final int A = 0xFFFFEB3B;
    public static final int B = 0xFF4CAF50;
    public static final int C = 0xFFF44336;

    /** 零序 / 其它通道沿用原来的青色系。 */
    public static final int ZERO = 0xFF64D2FF;

    private static final int[] FALLBACK = {
            0xFF9C27B0, 0xFFFF9800, 0xFFE91E63, 0xFF8BC34A,
            0xFF00BCD4, 0xFFFFB955, 0xFF9E9E9E
    };

    private PhaseColors() {}

    public static int of(ComtradeCase.AnalogChannel ch, int index) {
        String phase = ch == null || ch.phase == null ? "" : ch.phase.trim().toUpperCase();
        if (phase.equals("A")) return A;
        if (phase.equals("B")) return B;
        if (phase.equals("C")) return C;
        if (phase.equals("Z") || phase.equals("0") || phase.isEmpty()) return ZERO;
        return FALLBACK[Math.abs(index) % FALLBACK.length];
    }
}
