/*
 * xBR pixel-art scaler (2x, 3x, 4x).
 *
 * Java port of FFmpeg's libavfilter/vf_xbr.c, which is based on Hyllian's xBR shader:
 *   Copyright (c) 2011, 2012 Hyllian/Jararaca <sergiogdb@gmail.com>
 *   Copyright (c) 2014 Arwa Arif <arwaarif1994@gmail.com>
 *
 * This file is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This file is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser
 * General Public License (LICENSE-LGPL-2.1.txt) for more details.
 *
 * Changes in this port: Java instead of C (the FILT2/3/4 macros became methods driven by tables of
 * neighbour and output positions), the RGB to YUV table is computed per pixel instead, one thread.
 */
package brewemu.video;

/** xBR 2x / 3x / 4x on 0xAARRGGBB pixels (the result is fully opaque). */
public final class Xbr {
    private Xbr() {}

    // the 21 neighbours, named as in the original (PE is the centre pixel)
    private static final int PE = 0, PI = 1, PH = 2, PF = 3, PG = 4, PC = 5, PD = 6, PB = 7, PA = 8, G5 = 9, C4 = 10,
            G0 = 11, D0 = 12, C1 = 13, B1 = 14, F4 = 15, I4 = 16, H5 = 17, I5 = 18, A0 = 19, A1 = 20;

    /** The four rotations: which neighbour plays each role (the argument order of FILTn). */
    private static final int[][] ROT = {
        {PE, PI, PH, PF, PG, PC, PD, PB, PA, G5, C4, G0, D0, C1, B1, F4, I4, H5, I5, A0, A1},
        {PE, PC, PF, PB, PI, PA, PH, PD, PG, I4, A1, I5, H5, A0, D0, B1, C1, F4, C4, G5, G0},
        {PE, PA, PB, PD, PC, PG, PF, PH, PI, C1, G0, C4, F4, G5, H5, D0, A0, B1, A1, I4, I5},
        {PE, PG, PD, PH, PA, PI, PB, PF, PC, A0, I5, A1, B1, I4, F4, H5, G5, D0, G0, C1, C4},
    };

    private static int rc(int row, int col) { return row * 8 + col; }

    /** Output positions per rotation (row * 8 + col inside the n x n block), in the macros' argument order. */
    private static final int[][] OUT2 = {
        {rc(0,0), rc(0,1), rc(1,0), rc(1,1)},
        {rc(1,0), rc(0,0), rc(1,1), rc(0,1)},
        {rc(1,1), rc(1,0), rc(0,1), rc(0,0)},
        {rc(0,1), rc(1,1), rc(0,0), rc(1,0)},
    };
    private static final int[][] OUT3 = {
        {rc(0,0), rc(0,1), rc(0,2), rc(1,0), rc(1,1), rc(1,2), rc(2,0), rc(2,1), rc(2,2)},
        {rc(2,0), rc(1,0), rc(0,0), rc(2,1), rc(1,1), rc(0,1), rc(2,2), rc(1,2), rc(0,2)},
        {rc(2,2), rc(2,1), rc(2,0), rc(1,2), rc(1,1), rc(1,0), rc(0,2), rc(0,1), rc(0,0)},
        {rc(0,2), rc(1,2), rc(2,2), rc(0,1), rc(1,1), rc(2,1), rc(0,0), rc(1,0), rc(2,0)},
    };
    /** 4x: argument order N15, N14, N11, N3, N7, N10, N13, N12 (the only ones the macro writes). */
    private static final int[][] OUT4 = {
        {rc(3,3), rc(3,2), rc(2,3), rc(0,3), rc(1,3), rc(2,2), rc(3,1), rc(3,0)},
        {rc(0,3), rc(1,3), rc(0,2), rc(0,0), rc(0,1), rc(1,2), rc(2,3), rc(3,3)},
        {rc(0,0), rc(0,1), rc(1,0), rc(3,0), rc(2,0), rc(1,1), rc(0,2), rc(0,3)},
        {rc(3,0), rc(2,0), rc(3,1), rc(3,3), rc(3,2), rc(2,1), rc(1,0), rc(0,0)},
    };

    private static final int LB_MASK = 0x00FEFEFE, RED_BLUE_MASK = 0x00FF00FF, GREEN_MASK = 0x0000FF00;

    private static int df(int yuv1, int yuv2) {
        return (Math.abs((yuv1 & 0xff0000) - (yuv2 & 0xff0000)) >> 16)
             + (Math.abs((yuv1 & 0x00ff00) - (yuv2 & 0x00ff00)) >> 8)
             +  Math.abs((yuv1 & 0x0000ff) - (yuv2 & 0x0000ff));
    }

    private static boolean eq(int yuv1, int yuv2) { return df(yuv1, yuv2) < 155; }

    private static int blend128(int a, int b) { return ((a & LB_MASK) >> 1) + ((b & LB_MASK) >> 1); }

    private static int blend(int a, int b, int m, int s) {
        return (RED_BLUE_MASK & ((a & RED_BLUE_MASK) + ((((b & RED_BLUE_MASK) - (a & RED_BLUE_MASK)) * m) >>> s)))
             | (GREEN_MASK & ((a & GREEN_MASK) + ((((b & GREEN_MASK) - (a & GREEN_MASK)) * m) >>> s)));
    }
    private static int blend32(int a, int b) { return blend(a, b, 1, 3); }
    private static int blend64(int a, int b) { return blend(a, b, 1, 2); }
    private static int blend192(int a, int b) { return blend(a, b, 3, 2); }
    private static int blend224(int a, int b) { return blend(a, b, 7, 3); }

    /** One FILTn step for one rotation. p / y: the 21 neighbours and their YUV values; e: the output block. */
    private static void filt(int n, int[] p, int[] y, int[] r, int[] out, int[] e, int line, int base) {
        final int pe = p[r[0]], pi = p[r[1]], ph = p[r[2]], pf = p[r[3]], pg = p[r[4]], pc = p[r[5]], pd = p[r[6]], pb = p[r[7]];
        if (pe == ph || pe == pf) return;
        final int ye = y[r[0]], yi = y[r[1]], yh = y[r[2]], yf = y[r[3]], yg = y[r[4]], yc = y[r[5]], yd = y[r[6]], yb = y[r[7]];
        final int yh5 = y[r[17]], yf4 = y[r[15]], yi5 = y[r[18]], yi4 = y[r[16]];
        final int e1 = df(ye, yc) + df(ye, yg) + df(yi, yh5) + df(yi, yf4) + (df(yh, yf) << 2);
        final int i1 = df(yh, yd) + df(yh, yi5) + df(yf, yi4) + df(yf, yb) + (df(ye, yi) << 2);
        if (e1 > i1) return;
        final boolean pickF = df(ye, yf) <= df(ye, yh);
        final int px = pickF ? pf : ph;
        boolean strong;
        if (n == 3)
            strong = e1 < i1 && (!eq(yf, yb) && !eq(yf, yc) || !eq(yh, yd) && !eq(yh, yg) || eq(ye, yi)
                    && (!eq(yf, yf4) && !eq(yf, yi4) || !eq(yh, yh5) && !eq(yh, yi5))
                    || eq(ye, yg) || eq(ye, yc));
        else
            strong = e1 < i1 && (!eq(yf, yb) && !eq(yh, yd) || eq(ye, yi)
                    && (!eq(yf, yi4) && !eq(yh, yi5))
                    || eq(ye, yg) || eq(ye, yc));
        if (n == 2) {
            final int N1 = o(out[1], line, base), N2 = o(out[2], line, base), N3 = o(out[3], line, base);
            if (strong) {
                final int ke = df(yf, yg), ki = df(yh, yc);
                final boolean left = ke << 1 <= ki && pe != pg && pd != pg;
                final boolean up = ke >= ki << 1 && pe != pc && pb != pc;
                if (left && up) { e[N3] = blend224(e[N3], px); e[N2] = blend64(e[N2], px); e[N1] = e[N2]; }
                else if (left) { e[N3] = blend192(e[N3], px); e[N2] = blend64(e[N2], px); }
                else if (up) { e[N3] = blend192(e[N3], px); e[N1] = blend64(e[N1], px); }
                else e[N3] = blend128(e[N3], px);
            } else e[N3] = blend128(e[N3], px);
        } else if (n == 3) {
            final int N2 = o(out[2], line, base), N5 = o(out[5], line, base), N6 = o(out[6], line, base),
                      N7 = o(out[7], line, base), N8 = o(out[8], line, base);
            if (strong) {
                final int ke = df(yf, yg), ki = df(yh, yc);
                final boolean left = ke << 1 <= ki && pe != pg && pd != pg;
                final boolean up = ke >= ki << 1 && pe != pc && pb != pc;
                if (left && up) {
                    e[N7] = blend192(e[N7], px); e[N6] = blend64(e[N6], px);
                    e[N5] = e[N7]; e[N2] = e[N6]; e[N8] = px;
                } else if (left) {
                    e[N7] = blend192(e[N7], px); e[N5] = blend64(e[N5], px); e[N6] = blend64(e[N6], px); e[N8] = px;
                } else if (up) {
                    e[N5] = blend192(e[N5], px); e[N7] = blend64(e[N7], px); e[N2] = blend64(e[N2], px); e[N8] = px;
                } else {
                    e[N8] = blend224(e[N8], px); e[N5] = blend32(e[N5], px); e[N7] = blend32(e[N7], px);
                }
            } else e[N8] = blend128(e[N8], px);
        } else {
            final int N15 = o(out[0], line, base), N14 = o(out[1], line, base), N11 = o(out[2], line, base),
                      N3 = o(out[3], line, base), N7 = o(out[4], line, base), N10 = o(out[5], line, base),
                      N13 = o(out[6], line, base), N12 = o(out[7], line, base);
            if (strong) {
                final int ke = df(yf, yg), ki = df(yh, yc);
                final boolean left = ke << 1 <= ki && pe != pg && pd != pg;
                final boolean up = ke >= ki << 1 && pe != pc && pb != pc;
                if (left && up) {
                    e[N13] = blend192(e[N13], px); e[N12] = blend64(e[N12], px);
                    e[N15] = e[N14] = e[N11] = px;
                    e[N10] = e[N3] = e[N12];
                    e[N7] = e[N13];
                } else if (left) {
                    e[N11] = blend192(e[N11], px); e[N13] = blend192(e[N13], px);
                    e[N10] = blend64(e[N10], px); e[N12] = blend64(e[N12], px);
                    e[N14] = px; e[N15] = px;
                } else if (up) {
                    e[N14] = blend192(e[N14], px); e[N7] = blend192(e[N7], px);
                    e[N10] = blend64(e[N10], px); e[N3] = blend64(e[N3], px);
                    e[N11] = px; e[N15] = px;
                } else {
                    e[N11] = blend128(e[N11], px); e[N14] = blend128(e[N14], px); e[N15] = px;
                }
            } else e[N15] = blend128(e[N15], px);
        }
    }

    private static int o(int rc, int line, int base) { return base + (rc >> 3) * line + (rc & 7); }

    /**
     * Scales src (w x h) by n (2, 3 or 4) into dst (w*n x h*n). yuv is scratch space of at least w*h ints.
     */
    public static void scale(int[] src, int w, int h, int n, int[] dst, int[] yuv) {
        if (n < 2 || n > 4) throw new IllegalArgumentException("n must be 2, 3 or 4");
        for (int i = 0; i < w * h; i++) yuv[i] = Yuv.of(src[i]);
        final int line = w * n;
        final int[][] outs = n == 2 ? OUT2 : n == 3 ? OUT3 : OUT4;
        final int[] p = new int[21], y = new int[21];
        for (int row = 0; row < h; row++) {
            // rows: two up, one up, centre, one down, two down (clamped at the edges as in the original)
            int r2 = row, r1 = row - 1, r0 = row - 2, r3 = row + 1, r4 = row + 2;
            if (row <= 1) { r0 = r1; if (row == 0) { r0 = r1 = r2; } }
            if (row >= h - 2) { r4 = r3; if (row == h - 1) { r4 = r3 = r2; } }
            final int s0 = r0 * w, s1 = r1 * w, s2 = r2 * w, s3 = r3 * w, s4 = r4 * w;
            for (int x = 0; x < w; x++) {
                final int xp = x > 0 ? x - 1 : x;                    // pprev
                final int xp2 = x > 1 ? xp - 1 : xp;                 // pprev2
                final int xn = x < w - 1 ? x + 1 : x;                // pnext
                final int xn2 = x < w - 2 ? xn + 1 : xn;             // pnext2
                set(p, y, src, yuv, B1, s0 + x); set(p, y, src, yuv, PB, s1 + x); set(p, y, src, yuv, PE, s2 + x);
                set(p, y, src, yuv, PH, s3 + x); set(p, y, src, yuv, H5, s4 + x);
                set(p, y, src, yuv, A1, s0 + xp); set(p, y, src, yuv, PA, s1 + xp); set(p, y, src, yuv, PD, s2 + xp);
                set(p, y, src, yuv, PG, s3 + xp); set(p, y, src, yuv, G5, s4 + xp);
                set(p, y, src, yuv, A0, s1 + xp2); set(p, y, src, yuv, D0, s2 + xp2); set(p, y, src, yuv, G0, s3 + xp2);
                set(p, y, src, yuv, C1, s0 + xn); set(p, y, src, yuv, PC, s1 + xn); set(p, y, src, yuv, PF, s2 + xn);
                set(p, y, src, yuv, PI, s3 + xn); set(p, y, src, yuv, I5, s4 + xn);
                set(p, y, src, yuv, C4, s1 + xn2); set(p, y, src, yuv, F4, s2 + xn2); set(p, y, src, yuv, I4, s3 + xn2);

                final int base = row * n * line + x * n, pe = p[PE];
                for (int j = 0; j < n; j++) for (int i = 0; i < n; i++) dst[base + j * line + i] = pe;
                for (int k = 0; k < 4; k++) filt(n, p, y, ROT[k], outs[k], dst, line, base);
            }
        }
        for (int i = 0, m = w * n * h * n; i < m; i++) dst[i] |= 0xff000000;
    }

    private static void set(int[] p, int[] y, int[] src, int[] yuv, int k, int i) { p[k] = src[i]; y[k] = yuv[i]; }
}
