package com.pdzdh.calcutool;

/**
 * Business core, 1:1 port of the WinForm implementation (main.cs).
 *
 * <p>Input convention: A-phase quantities define the reference frame. Ua uses the declared
 * angle as its own base angle, while every other quantity is entered as an angle relative to
 * that same base, then rotated back by the base when it is displayed. This keeps the numbers a
 * technician reads off a power analyser consistent with the phasor diagram.</p>
 */
public final class PhasorCalc {

    public static final int UA = 0, UB = 1, UC = 2, IA = 3, IB = 4, IC = 5;

    private static final double SQ3 = Math.sqrt(3.0);
    private static final Complex A1 = new Complex(-0.5, SQ3 / 2.0);
    private static final Complex A2 = new Complex(-0.5, -SQ3 / 2.0);

    private PhasorCalc() {}

    /** Result bundle handed to the UI and the phasor view. */
    public static final class Result {
        public double baseAngle;
        public Complex va, vb, vc, ia, ib, ic;
        public Complex uab, ubc, uca;
        public Complex v1, v2, v0, i1, i2, i0;
        public double vuf, v0uf, iuf, i0uf;
        public Complex sa, sb, sc, st;
        public double pfT, pfA, pfB, pfC;
    }

    /** @param mag magnitude per quantity (see index constants) @param ang angle in degrees */
    public static Result compute(double[] mag, double[] ang) {
        Result r = new Result();

        r.baseAngle = ang[UA];
        r.va = Complex.polar(mag[UA], 0);
        r.vb = relative(mag[UB], ang[UB], r.baseAngle);
        r.vc = relative(mag[UC], ang[UC], r.baseAngle);
        r.ia = relative(mag[IA], ang[IA], r.baseAngle);
        r.ib = relative(mag[IB], ang[IB], r.baseAngle);
        r.ic = relative(mag[IC], ang[IC], r.baseAngle);

        Complex[] vs = sequence(r.va, r.vb, r.vc);
        Complex[] is = sequence(r.ia, r.ib, r.ic);
        r.v1 = vs[0]; r.v2 = vs[1]; r.v0 = vs[2];
        r.i1 = is[0]; r.i2 = is[1]; r.i0 = is[2];

        r.vuf  = r.v1.mag() > 0 ? r.v2.mag() / r.v1.mag() * 100 : 0;
        r.v0uf = r.v1.mag() > 0 ? r.v0.mag() / r.v1.mag() * 100 : 0;
        r.iuf  = r.i1.mag() > 0 ? r.i2.mag() / r.i1.mag() * 100 : 0;
        r.i0uf = r.i1.mag() > 0 ? r.i0.mag() / r.i1.mag() * 100 : 0;

        r.uab = r.va.sub(r.vb);
        r.ubc = r.vb.sub(r.vc);
        r.uca = r.vc.sub(r.va);

        r.sa = r.va.mul(r.ia.conj());
        r.sb = r.vb.mul(r.ib.conj());
        r.sc = r.vc.mul(r.ic.conj());
        r.st = r.sa.add(r.sb).add(r.sc);

        r.pfT = r.st.mag() > 0 ? r.st.re / r.st.mag() : 0;
        r.pfA = r.sa.mag() > 0 ? r.sa.re / r.sa.mag() : 0;
        r.pfB = r.sb.mag() > 0 ? r.sb.re / r.sb.mag() : 0;
        r.pfC = r.sc.mag() > 0 ? r.sc.re / r.sc.mag() : 0;
        return r;
    }

    private static Complex relative(double magnitude, double angleDeg, double baseAngleDeg) {
        return Complex.polar(magnitude, Math.toRadians(angleDeg - baseAngleDeg));
    }

    /** Symmetrical components, returned scaled by 3 (3U1 / 3U2 / 3U0) as in the desktop tool. */
    private static Complex[] sequence(Complex xa, Complex xb, Complex xc) {
        Complex zero = xa.add(xb).add(xc).div(3);
        Complex pos  = xa.add(A1.mul(xb)).add(A2.mul(xc)).div(3);
        Complex neg  = xa.add(A2.mul(xb)).add(A1.mul(xc)).div(3);
        return new Complex[] { pos.mul(new Complex(3, 0)), neg.mul(new Complex(3, 0)),
                               zero.mul(new Complex(3, 0)) };
    }

    /** Display angle: relative argument + base angle, wrapped into (-180, 180]. */
    public static double displayAngle(Complex c, double baseAngle) {
        return norm(c.argDeg() + baseAngle);
    }

    public static double norm(double deg) {
        double d = deg % 360.0;
        if (d > 180) return d - 360;
        if (d < -180) return d + 360;
        return d;
    }
}
