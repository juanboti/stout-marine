/*
 * RGB to YUV as used by FFmpeg's hqx and xbr filters (their 16 MB lookup table, computed per pixel instead).
 * From FFmpeg's libavfilter/vf_hqx.c (ISC licence, Copyright (c) 2014 Clément Bœsch) and vf_xbr.c.
 */
package brewemu.video;

final class Yuv {
    private Yuv() {}

    /** 0x00YYUUVV for an 0x..RRGGBB pixel, bit-identical to FFmpeg's table. */
    static int of(int c) {
        final int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        final int rg = r - g, bg = b - g;
        final int u = (-169 * rg + 500 * bg) / 1000 + 128;
        final int v = (500 * rg - 81 * bg) / 1000 + 128;
        final int startg = Math.max(Math.max(-bg, -rg), 0);
        final int y = (299 * rg + 1000 * startg + 114 * bg) / 1000 + (g - startg);
        return (y << 16) + (u << 8) + v;
    }
}
