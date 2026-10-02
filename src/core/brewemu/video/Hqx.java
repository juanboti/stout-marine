/*
 * hqx magnification (hq2x, hq3x, hq4x) for pixel art.
 *
 * Java port of FFmpeg's libavfilter/vf_hqx.c:
 *   Copyright (c) 2014 Clément Bœsch
 *
 *   Permission to use, copy, modify, and/or distribute this software for any
 *   purpose with or without fee is hereby granted, provided that the above
 *   copyright notice and this permission notice appear in all copies.
 *
 *   THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
 *   WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 *   MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR
 *   ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 *   WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
 *   ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF
 *   OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
 *
 * The hqx algorithm was designed by Maxim Stepin. Changes in this port: Java instead of C, the RGB to YUV
 * table is replaced by the same calculation done per pixel, one thread.
 */
package brewemu.video;

/** hq2x / hq3x / hq4x on 0xAARRGGBB pixels (the result is fully opaque). */
public final class Hqx {
    private Hqx() {}

    private static boolean yuvDiff(int yuv1, int yuv2) {
        return Math.abs((yuv1 & 0xff0000) - (yuv2 & 0xff0000)) > (48 << 16)
            || Math.abs((yuv1 & 0x00ff00) - (yuv2 & 0x00ff00)) > (7 << 8)
            || Math.abs((yuv1 & 0x0000ff) - (yuv2 & 0x0000ff)) > 6;
    }


    /** (c1*w1 + c2*w2) >> s */
    private static int i2(int c1, int w1, int c2, int w2, int s) {
        return (((((c1 & 0xff00ff00) >>> 8) * w1 + ((c2 & 0xff00ff00) >>> 8) * w2) << (8 - s)) & 0xff00ff00)
             | ((((c1 & 0x00ff00ff) * w1 + (c2 & 0x00ff00ff) * w2) >>> s) & 0x00ff00ff);
    }

    /** (c1*w1 + c2*w2 + c3*w3) >> s */
    private static int i3(int c1, int w1, int c2, int w2, int c3, int w3, int s) {
        return (((((c1 & 0xff00ff00) >>> 8) * w1 + ((c2 & 0xff00ff00) >>> 8) * w2 + ((c3 & 0xff00ff00) >>> 8) * w3) << (8 - s)) & 0xff00ff00)
             | ((((c1 & 0x00ff00ff) * w1 + (c2 & 0x00ff00ff) * w2 + (c3 & 0x00ff00ff) * w3) >>> s) & 0x00ff00ff);
    }

    private static int drop4(int z) { return z > 4 ? z - 1 : z; }

    /** Moves bit n (4-adjusted) of the pattern to the position of p[n] (the "shuffled" mask). */
    private static int shuffle(int k, boolean rot, int[] p) {
        int r = 0;
        for (int n = 0; n <= 8; n++) {
            if (n == 4) continue;
            r |= ((k >> (rot ? 7 - drop4(n) : drop4(n))) & 1) << drop4(p[n]);
        }
        return r;
    }

    // the pattern tests: P(m, r) is (ks & m) == r
    private static boolean P(int ks, int m, int r) { return (ks & m) == r; }

    private static int hq2x1x1(int k, int[] w, int[] yv, int[] p, int[] shuf) {
        final int ks = shuf[k];
        final int w0 = w[p[0]], w1 = w[p[1]], w3 = w[p[3]], w4 = w[p[4]];
        final int y1 = yv[p[1]], y3 = yv[p[3]], y5 = yv[p[5]], y7 = yv[p[7]];
        if ((P(ks,0xbf,0x37) || P(ks,0xdb,0x13)) && yuvDiff(y1, y5)) return i2(w4, 3, w3, 1, 2);
        if ((P(ks,0xdb,0x49) || P(ks,0xef,0x6d)) && yuvDiff(y7, y3)) return i2(w4, 3, w1, 1, 2);
        if ((P(ks,0x0b,0x0b) || P(ks,0xfe,0x4a) || P(ks,0xfe,0x1a)) && yuvDiff(y3, y1)) return w4;
        if ((P(ks,0x6f,0x2a) || P(ks,0x5b,0x0a) || P(ks,0xbf,0x3a) || P(ks,0xdf,0x5a) ||
             P(ks,0x9f,0x8a) || P(ks,0xcf,0x8a) || P(ks,0xef,0x4e) || P(ks,0x3f,0x0e) ||
             P(ks,0xfb,0x5a) || P(ks,0xbb,0x8a) || P(ks,0x7f,0x5a) || P(ks,0xaf,0x8a) ||
             P(ks,0xeb,0x8a)) && yuvDiff(y3, y1)) return i2(w4, 3, w0, 1, 2);
        if (P(ks,0x0b,0x08)) return i3(w4, 2, w0, 1, w1, 1, 2);
        if (P(ks,0x0b,0x02)) return i3(w4, 2, w0, 1, w3, 1, 2);
        if (P(ks,0x2f,0x2f)) return i3(w4, 14, w3, 1, w1, 1, 4);
        if (P(ks,0xbf,0x37) || P(ks,0xdb,0x13)) return i3(w4, 5, w1, 2, w3, 1, 3);
        if (P(ks,0xdb,0x49) || P(ks,0xef,0x6d)) return i3(w4, 5, w3, 2, w1, 1, 3);
        if (P(ks,0x1b,0x03) || P(ks,0x4f,0x43) || P(ks,0x8b,0x83) || P(ks,0x6b,0x43)) return i2(w4, 3, w3, 1, 2);
        if (P(ks,0x4b,0x09) || P(ks,0x8b,0x89) || P(ks,0x1f,0x19) || P(ks,0x3b,0x19)) return i2(w4, 3, w1, 1, 2);
        if (P(ks,0x7e,0x2a) || P(ks,0xef,0xab) || P(ks,0xbf,0x8f) || P(ks,0x7e,0x0e)) return i3(w4, 2, w3, 3, w1, 3, 3);
        if (P(ks,0xfb,0x6a) || P(ks,0x6f,0x6e) || P(ks,0x3f,0x3e) || P(ks,0xfb,0xfa) ||
            P(ks,0xdf,0xde) || P(ks,0xdf,0x1e)) return i2(w4, 3, w0, 1, 2);
        if (P(ks,0x0a,0x00) || P(ks,0x4f,0x4b) || P(ks,0x9f,0x1b) || P(ks,0x2f,0x0b) ||
            P(ks,0xbe,0x0a) || P(ks,0xee,0x0a) || P(ks,0x7e,0x0a) || P(ks,0xeb,0x4b) ||
            P(ks,0x3b,0x1b)) return i3(w4, 2, w3, 1, w1, 1, 2);
        return i3(w4, 6, w3, 1, w1, 1, 3);
    }

    /** dst position of "pos" (0..3 = 00, 01, 10, 11 of a 2x2 block) relative to base. */
    private static int at(int base, int line, int pos) { return base + line * (pos >> 1) + (pos & 1); }

    private static void hq3x2x1(int[] dst, int base, int line, int k, int[] w, int[] yv, int pos00, int pos01, int[] p, int[] shuf) {
        final int ks = shuf[k];
        final int w0 = w[p[0]], w1 = w[p[1]], w3 = w[p[3]], w4 = w[p[4]];
        final int y1 = yv[p[1]], y3 = yv[p[3]], y5 = yv[p[5]], y7 = yv[p[7]];
        int d00, d01;
        if ((P(ks,0xdb,0x49) || P(ks,0xef,0x6d)) && yuvDiff(y7, y3)) d00 = i2(w4, 3, w1, 1, 2);
        else if ((P(ks,0xbf,0x37) || P(ks,0xdb,0x13)) && yuvDiff(y1, y5)) d00 = i2(w4, 3, w3, 1, 2);
        else if ((P(ks,0x0b,0x0b) || P(ks,0xfe,0x4a) || P(ks,0xfe,0x1a)) && yuvDiff(y3, y1)) d00 = w4;
        else if ((P(ks,0x6f,0x2a) || P(ks,0x5b,0x0a) || P(ks,0xbf,0x3a) || P(ks,0xdf,0x5a) ||
                  P(ks,0x9f,0x8a) || P(ks,0xcf,0x8a) || P(ks,0xef,0x4e) || P(ks,0x3f,0x0e) ||
                  P(ks,0xfb,0x5a) || P(ks,0xbb,0x8a) || P(ks,0x7f,0x5a) || P(ks,0xaf,0x8a) ||
                  P(ks,0xeb,0x8a)) && yuvDiff(y3, y1)) d00 = i2(w4, 3, w0, 1, 2);
        else if (P(ks,0x4b,0x09) || P(ks,0x8b,0x89) || P(ks,0x1f,0x19) || P(ks,0x3b,0x19)) d00 = i2(w4, 3, w1, 1, 2);
        else if (P(ks,0x1b,0x03) || P(ks,0x4f,0x43) || P(ks,0x8b,0x83) || P(ks,0x6b,0x43)) d00 = i2(w4, 3, w3, 1, 2);
        else if (P(ks,0x7e,0x2a) || P(ks,0xef,0xab) || P(ks,0xbf,0x8f) || P(ks,0x7e,0x0e)) d00 = i2(w3, 1, w1, 1, 1);
        else if (P(ks,0x4f,0x4b) || P(ks,0x9f,0x1b) || P(ks,0x2f,0x0b) || P(ks,0xbe,0x0a) ||
                 P(ks,0xee,0x0a) || P(ks,0x7e,0x0a) || P(ks,0xeb,0x4b) || P(ks,0x3b,0x1b)) d00 = i3(w4, 2, w3, 7, w1, 7, 4);
        else if (P(ks,0x0b,0x08) || P(ks,0xf9,0x68) || P(ks,0xf3,0x62) || P(ks,0x6d,0x6c) ||
                 P(ks,0x67,0x66) || P(ks,0x3d,0x3c) || P(ks,0x37,0x36) || P(ks,0xf9,0xf8) ||
                 P(ks,0xdd,0xdc) || P(ks,0xf3,0xf2) || P(ks,0xd7,0xd6) || P(ks,0xdd,0x1c) ||
                 P(ks,0xd7,0x16) || P(ks,0x0b,0x02)) d00 = i2(w4, 3, w0, 1, 2);
        else d00 = i3(w4, 2, w3, 1, w1, 1, 2);

        if ((P(ks,0xfe,0xde) || P(ks,0x9e,0x16) || P(ks,0xda,0x12) || P(ks,0x17,0x16) ||
             P(ks,0x5b,0x12) || P(ks,0xbb,0x12)) && yuvDiff(y1, y5)) d01 = w4;
        else if ((P(ks,0x0f,0x0b) || P(ks,0x5e,0x0a) || P(ks,0xfb,0x7b) || P(ks,0x3b,0x0b) ||
                  P(ks,0xbe,0x0a) || P(ks,0x7a,0x0a)) && yuvDiff(y3, y1)) d01 = w4;
        else if (P(ks,0xbf,0x8f) || P(ks,0x7e,0x0e) || P(ks,0xbf,0x37) || P(ks,0xdb,0x13)) d01 = i2(w1, 3, w4, 1, 2);
        else if (P(ks,0x02,0x00) || P(ks,0x7c,0x28) || P(ks,0xed,0xa9) || P(ks,0xf5,0xb4) ||
                 P(ks,0xd9,0x90)) d01 = i2(w4, 3, w1, 1, 2);
        else if (P(ks,0x4f,0x4b) || P(ks,0xfb,0x7b) || P(ks,0xfe,0x7e) || P(ks,0x9f,0x1b) ||
                 P(ks,0x2f,0x0b) || P(ks,0xbe,0x0a) || P(ks,0x7e,0x0a) || P(ks,0xfb,0x4b) ||
                 P(ks,0xfb,0xdb) || P(ks,0xfe,0xde) || P(ks,0xfe,0x56) || P(ks,0x57,0x56) ||
                 P(ks,0x97,0x16) || P(ks,0x3f,0x1e) || P(ks,0xdb,0x12) || P(ks,0xbb,0x12)) d01 = i2(w4, 7, w1, 1, 3);
        else d01 = w4;
        dst[at(base, line, pos00)] = d00;
        dst[at(base, line, pos01)] = d01;
    }

    private static void hq4x2x2(int[] dst, int base, int line, int k, int[] w, int[] yv, int pos00, int pos01, int pos10, int pos11, int[] p, int[] shuf) {
        final int ks = shuf[k];
        final int w0 = w[p[0]], w1 = w[p[1]], w3 = w[p[3]], w4 = w[p[4]];
        final int y1 = yv[p[1]], y3 = yv[p[3]], y5 = yv[p[5]], y7 = yv[p[7]];

        final boolean cond00 = (P(ks,0xbf,0x37) || P(ks,0xdb,0x13)) && yuvDiff(y1, y5);
        final boolean cond01 = (P(ks,0xdb,0x49) || P(ks,0xef,0x6d)) && yuvDiff(y7, y3);
        final boolean cond02 = (P(ks,0x6f,0x2a) || P(ks,0x5b,0x0a) || P(ks,0xbf,0x3a) ||
                                P(ks,0xdf,0x5a) || P(ks,0x9f,0x8a) || P(ks,0xcf,0x8a) ||
                                P(ks,0xef,0x4e) || P(ks,0x3f,0x0e) || P(ks,0xfb,0x5a) ||
                                P(ks,0xbb,0x8a) || P(ks,0x7f,0x5a) || P(ks,0xaf,0x8a) ||
                                P(ks,0xeb,0x8a)) && yuvDiff(y3, y1);
        final boolean cond03 = P(ks,0xdb,0x49) || P(ks,0xef,0x6d);
        final boolean cond04 = P(ks,0xbf,0x37) || P(ks,0xdb,0x13);
        final boolean cond05 = P(ks,0x1b,0x03) || P(ks,0x4f,0x43) || P(ks,0x8b,0x83) || P(ks,0x6b,0x43);
        final boolean cond06 = P(ks,0x4b,0x09) || P(ks,0x8b,0x89) || P(ks,0x1f,0x19) || P(ks,0x3b,0x19);
        final boolean cond07 = P(ks,0x0b,0x08) || P(ks,0xf9,0x68) || P(ks,0xf3,0x62) ||
                               P(ks,0x6d,0x6c) || P(ks,0x67,0x66) || P(ks,0x3d,0x3c) ||
                               P(ks,0x37,0x36) || P(ks,0xf9,0xf8) || P(ks,0xdd,0xdc) ||
                               P(ks,0xf3,0xf2) || P(ks,0xd7,0xd6) || P(ks,0xdd,0x1c) ||
                               P(ks,0xd7,0x16) || P(ks,0x0b,0x02);
        final boolean cond08 = (P(ks,0x0f,0x0b) || P(ks,0x2b,0x0b) || P(ks,0xfe,0x4a) || P(ks,0xfe,0x1a)) && yuvDiff(y3, y1);
        final boolean cond09 = P(ks,0x2f,0x2f);
        final boolean cond10 = P(ks,0x0a,0x00);
        final boolean cond11 = P(ks,0x0b,0x09);
        final boolean cond12 = P(ks,0x7e,0x2a) || P(ks,0xef,0xab);
        final boolean cond13 = P(ks,0xbf,0x8f) || P(ks,0x7e,0x0e);
        final boolean cond14 = P(ks,0x4f,0x4b) || P(ks,0x9f,0x1b) || P(ks,0x2f,0x0b) ||
                               P(ks,0xbe,0x0a) || P(ks,0xee,0x0a) || P(ks,0x7e,0x0a) ||
                               P(ks,0xeb,0x4b) || P(ks,0x3b,0x1b);
        final boolean cond15 = P(ks,0x0b,0x03);

        int d00, d01, d10, d11;
        if (cond00) d00 = i2(w4, 5, w3, 3, 3);
        else if (cond01) d00 = i2(w4, 5, w1, 3, 3);
        else if ((P(ks,0x0b,0x0b) || P(ks,0xfe,0x4a) || P(ks,0xfe,0x1a)) && yuvDiff(y3, y1)) d00 = w4;
        else if (cond02) d00 = i2(w4, 5, w0, 3, 3);
        else if (cond03) d00 = i2(w4, 3, w3, 1, 2);
        else if (cond04) d00 = i2(w4, 3, w1, 1, 2);
        else if (cond05) d00 = i2(w4, 5, w3, 3, 3);
        else if (cond06) d00 = i2(w4, 5, w1, 3, 3);
        else if (P(ks,0x0f,0x0b) || P(ks,0x5e,0x0a) || P(ks,0x2b,0x0b) || P(ks,0xbe,0x0a) ||
                 P(ks,0x7a,0x0a) || P(ks,0xee,0x0a)) d00 = i2(w1, 1, w3, 1, 1);
        else if (cond07) d00 = i2(w4, 5, w0, 3, 3);
        else d00 = i3(w4, 2, w1, 1, w3, 1, 2);

        if (cond00) d01 = i2(w4, 7, w3, 1, 3);
        else if (cond08) d01 = w4;
        else if (cond02) d01 = i2(w4, 3, w0, 1, 2);
        else if (cond09) d01 = w4;
        else if (cond10) d01 = i3(w4, 5, w1, 2, w3, 1, 3);
        else if (P(ks,0x0b,0x08)) d01 = i3(w4, 5, w1, 2, w0, 1, 3);
        else if (cond11) d01 = i2(w4, 5, w1, 3, 3);
        else if (cond04) d01 = i2(w1, 3, w4, 1, 2);
        else if (cond12) d01 = i3(w1, 2, w4, 1, w3, 1, 2);
        else if (cond13) d01 = i2(w1, 5, w3, 3, 3);
        else if (cond05) d01 = i2(w4, 7, w3, 1, 3);
        else if (P(ks,0xf3,0x62) || P(ks,0x67,0x66) || P(ks,0x37,0x36) || P(ks,0xf3,0xf2) ||
                 P(ks,0xd7,0xd6) || P(ks,0xd7,0x16) || P(ks,0x0b,0x02)) d01 = i2(w4, 3, w0, 1, 2);
        else if (cond14) d01 = i2(w1, 1, w4, 1, 1);
        else d01 = i2(w4, 3, w1, 1, 2);

        if (cond01) d10 = i2(w4, 7, w1, 1, 3);
        else if (cond08) d10 = w4;
        else if (cond02) d10 = i2(w4, 3, w0, 1, 2);
        else if (cond09) d10 = w4;
        else if (cond10) d10 = i3(w4, 5, w3, 2, w1, 1, 3);
        else if (P(ks,0x0b,0x02)) d10 = i3(w4, 5, w3, 2, w0, 1, 3);
        else if (cond15) d10 = i2(w4, 5, w3, 3, 3);
        else if (cond03) d10 = i2(w3, 3, w4, 1, 2);
        else if (cond13) d10 = i3(w3, 2, w4, 1, w1, 1, 2);
        else if (cond12) d10 = i2(w3, 5, w1, 3, 3);
        else if (cond06) d10 = i2(w4, 7, w1, 1, 3);
        else if (P(ks,0x0b,0x08) || P(ks,0xf9,0x68) || P(ks,0x6d,0x6c) || P(ks,0x3d,0x3c) ||
                 P(ks,0xf9,0xf8) || P(ks,0xdd,0xdc) || P(ks,0xdd,0x1c)) d10 = i2(w4, 3, w0, 1, 2);
        else if (cond14) d10 = i2(w3, 1, w4, 1, 1);
        else d10 = i2(w4, 3, w3, 1, 2);

        if ((P(ks,0x7f,0x2b) || P(ks,0xef,0xab) || P(ks,0xbf,0x8f) || P(ks,0x7f,0x0f)) && yuvDiff(y3, y1)) d11 = w4;
        else if (cond02) d11 = i2(w4, 7, w0, 1, 3);
        else if (cond15) d11 = i2(w4, 7, w3, 1, 3);
        else if (cond11) d11 = i2(w4, 7, w1, 1, 3);
        else if (P(ks,0x0a,0x00) || P(ks,0x7e,0x2a) || P(ks,0xef,0xab) || P(ks,0xbf,0x8f) ||
                 P(ks,0x7e,0x0e)) d11 = i3(w4, 6, w3, 1, w1, 1, 3);
        else if (cond07) d11 = i2(w4, 7, w0, 1, 3);
        else d11 = w4;

        dst[at(base, line, pos00)] = d00;
        dst[at(base, line, pos01)] = d01;
        dst[at(base, line, pos10)] = d10;
        dst[at(base, line, pos11)] = d11;
    }

    private static final int[] ID = {0,1,2,3,4,5,6,7,8}, VM = {2,1,0,5,4,3,8,7,6}, HM = {6,7,8,3,4,5,0,1,2}, CM = {8,7,6,5,4,3,2,1,0};
    private static final int[] ROT_R = {2,5,8,1,4,7,0,3,6}, ROT_L = {6,3,0,7,4,1,8,5,2};
    private static final int[] S_ID = table(ID, false), S_VM = table(VM, false), S_HM = table(HM, false), S_CM = table(CM, false),
            S_ROT_R = table(ROT_R, true), S_ROT_L = table(ROT_L, true);

    private static int[] table(int[] p, boolean rot) {
        int[] t = new int[256];
        for (int k = 0; k < 256; k++) t[k] = shuffle(k, rot, p);
        return t;
    }

    /**
     * Scales src (w x h) by n (2, 3 or 4) into dst (w*n x h*n). yuv is scratch space of at least w*h ints.
     */
    public static void scale(int[] src, int w, int h, int n, int[] dst, int[] yuv) {
        for (int i = 0; i < w * h; i++) yuv[i] = Yuv.of(src[i]);
        final int line = w * n;
        final int[] px = new int[9], py = new int[9], off = new int[9];
        for (int y = 0; y < h; y++) {
            final int prev = y > 0 ? -w : 0, next = y < h - 1 ? w : 0;
            for (int x = 0; x < w; x++) {
                final int c = y * w + x, pc = x > 0 ? -1 : 0, nc = x < w - 1 ? 1 : 0;
                off[0] = pc + prev; off[1] = prev; off[2] = prev + nc;
                off[3] = pc;        off[4] = 0;    off[5] = nc;
                off[6] = pc + next; off[7] = next; off[8] = next + nc;
                for (int i = 0; i < 9; i++) { px[i] = src[c + off[i]]; py[i] = yuv[c + off[i]]; }
                final int y1 = py[4];
                int pattern = 0;
                for (int i = 0, bit = 0; i < 9; i++) {
                    if (i == 4) continue;
                    if (px[4] != px[i] && yuvDiff(y1, py[i])) pattern |= 1 << bit;
                    bit++;
                }
                final int base = y * n * line + x * n;
                if (n == 2) {
                    dst[base] = hq2x1x1(pattern, px, py, ID, S_ID);
                    dst[base + 1] = hq2x1x1(pattern, px, py, VM, S_VM);
                    dst[base + line] = hq2x1x1(pattern, px, py, HM, S_HM);
                    dst[base + line + 1] = hq2x1x1(pattern, px, py, CM, S_CM);
                } else if (n == 3) {
                    hq3x2x1(dst, base, line, pattern, px, py, 0, 1, ID, S_ID);
                    hq3x2x1(dst, base + 1, line, pattern, px, py, 1, 3, ROT_R, S_ROT_R);
                    hq3x2x1(dst, base + line, line, pattern, px, py, 2, 0, ROT_L, S_ROT_L);
                    hq3x2x1(dst, base + line + 1, line, pattern, px, py, 3, 2, CM, S_CM);
                    dst[base + line + 1] = px[4];
                } else if (n == 4) {
                    hq4x2x2(dst, base, line, pattern, px, py, 0, 1, 2, 3, ID, S_ID);
                    hq4x2x2(dst, base + 2, line, pattern, px, py, 1, 0, 3, 2, VM, S_VM);
                    hq4x2x2(dst, base + 2 * line, line, pattern, px, py, 2, 3, 0, 1, HM, S_HM);
                    hq4x2x2(dst, base + 2 * line + 2, line, pattern, px, py, 3, 2, 1, 0, CM, S_CM);
                } else throw new IllegalArgumentException("n must be 2, 3 or 4");
            }
        }
        for (int i = 0, m = w * n * h * n; i < m; i++) dst[i] |= 0xff000000;
    }
}
