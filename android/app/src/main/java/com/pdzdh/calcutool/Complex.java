package com.pdzdh.calcutool;

/** Minimal immutable complex number (System.Numerics.Complex equivalent). */
public final class Complex {
    public static final Complex ZERO = new Complex(0, 0);

    public final double re;
    public final double im;

    public Complex(double re, double im) {
        this.re = re;
        this.im = im;
    }

    public static Complex polar(double magnitude, double radians) {
        return new Complex(magnitude * Math.cos(radians), magnitude * Math.sin(radians));
    }

    public Complex add(Complex o) { return new Complex(re + o.re, im + o.im); }
    public Complex sub(Complex o) { return new Complex(re - o.re, im - o.im); }

    public Complex mul(Complex o) {
        return new Complex(re * o.re - im * o.im, re * o.im + im * o.re);
    }

    public Complex div(double s) { return new Complex(re / s, im / s); }

    public Complex conj() { return new Complex(re, -im); }

    public double mag() { return Math.hypot(re, im); }

    public double arg() { return Math.atan2(im, re); }

    public double argDeg() { return Math.toDegrees(Math.atan2(im, re)); }
}
