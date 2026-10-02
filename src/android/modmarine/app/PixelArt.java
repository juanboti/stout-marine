package modmarine.app;

import java.util.Random;

/** Tiny software painter for the pixel-art control deck (one array element = one art pixel). */
final class PixelArt {
    // palette
    static final int K = 0xFF080707, D1 = 0xFF1A1716, D2 = 0xFF282321, D3 = 0xFF37312D, D4 = 0xFF4E463F,
            D5 = 0xFF70655A, BONE = 0xFFE2D6BE, BONE2 = 0xFFA09480, R1 = 0xFF5C120C, R2 = 0xFF961E12,
            R3 = 0xFFCE3A1E, OR = 0xFFF48C28, OR2 = 0xFFFFC85A;

    final int w, h;
    final int[] px;

    PixelArt(int w, int h) { this.w = w; this.h = h; px = new int[w * h]; }

    void clear(int c) { java.util.Arrays.fill(px, c); }

    void set(int x, int y, int c) { if (x >= 0 && y >= 0 && x < w && y < h) px[y * w + x] = c; }
    int get(int x, int y) { return (x >= 0 && y >= 0 && x < w && y < h) ? px[y * w + x] : 0; }

    void rect(int x0, int y0, int x1, int y1, int c) {
        for (int y = Math.max(0, y0); y <= Math.min(h - 1, y1); y++)
            for (int x = Math.max(0, x0); x <= Math.min(w - 1, x1); x++) px[y * w + x] = c;
    }
    void hline(int x0, int x1, int y, int c) { rect(x0, y, x1, y, c); }
    void vline(int x, int y0, int y1, int c) { rect(x, y0, x, y1, c); }

    /** Filled ellipse inside the inclusive box, tested at pixel centres. */
    void ellipse(int x0, int y0, int x1, int y1, int c) {
        double cx = (x0 + x1) / 2.0, cy = (y0 + y1) / 2.0, rx = (x1 - x0) / 2.0 + 0.5, ry = (y1 - y0) / 2.0 + 0.5;
        for (int y = y0; y <= y1; y++)
            for (int x = x0; x <= x1; x++) {
                double dx = (x - cx) / rx, dy = (y - cy) / ry;
                if (dx * dx + dy * dy <= 1.0) set(x, y, c);
            }
    }

    // ------------------------------------------------------------ building blocks
    void panel(int x0, int y0, int x1, int y1, long seed) {
        Random r = new Random(seed);
        rect(x0, y0, x1, y1, D3);
        for (int y = y0; y <= y1; y++)
            for (int x = x0; x <= x1; x++) {
                float n = r.nextFloat();
                if (n < 0.10f) set(x, y, D2); else if (n < 0.14f) set(x, y, D4);
            }
        for (int y = y0 + 30; y < y1; y += 40) { hline(x0, x1, y, D1); hline(x0, x1, y + 1, D4); }
        hline(x0, x1, y0, D5); hline(x0, x1, y0 + 1, D4); hline(x0, x1, y0 + 2, R2);
        hline(x0, x1, y0 + 3, R1); hline(x0, x1, y0 + 4, D1);
        rivet(x0 + 3, y0 + 8); rivet(x1 - 4, y0 + 8); rivet(x0 + 3, y1 - 4); rivet(x1 - 4, y1 - 4);
    }

    void rivet(int x, int y) { set(x, y, D5); set(x + 1, y, D4); set(x, y + 1, D4); set(x + 1, y + 1, D1); }

    void bevelBox(int x0, int y0, int x1, int y1, int face, int hi, int lo, boolean inset) {
        rect(x0 - 1, y0 - 1, x1 + 1, y1 + 1, K);
        rect(x0, y0, x1, y1, face);
        int a = inset ? lo : hi, b = inset ? hi : lo;
        hline(x0, x1, y0, a); vline(x0, y0, y1, a);
        hline(x0, x1, y1, b); vline(x1, y0, y1, b);
        set(x0, y0, K); set(x1, y0, K); set(x0, y1, K); set(x1, y1, K);
    }

    void well(int x0, int y0, int x1, int y1) {
        rect(x0, y0, x1, y1, D1);
        hline(x0, x1, y0, K); vline(x0, y0, y1, K);
        hline(x0, x1, y1, D4); vline(x1, y0, y1, D4);
    }

    void pixDisc(int cx, int cy, int r, int face, int hi, int lo) {
        ellipse(cx - r - 1, cy - r - 1, cx + r + 1, cy + r + 1, K);
        ellipse(cx - r, cy - r, cx + r, cy + r, lo);
        ellipse(cx - r, cy - r, cx + r - 1, cy + r - 1, face);
        ellipse(cx - r + 2, cy - r + 1, cx + r - 3, cy - 1, hi);
        ellipse(cx - r + 2, cy - r + 3, cx + r - 3, cy + r - 3, face);
    }

    // ------------------------------------------------------------ bitmaps
    void blit(String[] b, int x, int y, int c, boolean shadow) {
        if (shadow)
            for (int j = 0; j < b.length; j++)
                for (int i = 0; i < b[j].length(); i++)
                    if (b[j].charAt(i) == '#' && get(x + i + 1, y + j + 1) != c) set(x + i + 1, y + j + 1, K);
        for (int j = 0; j < b.length; j++)
            for (int i = 0; i < b[j].length(); i++)
                if (b[j].charAt(i) == '#') set(x + i, y + j, c);
    }

    void blitCenter(String[] b, int cx, int cy, int c, boolean shadow) {
        blit(b, cx - b[0].length() / 2, cy - b.length / 2, c, shadow);
    }

    /** Small pixel font; scale = size of one glyph pixel in art pixels. */
    void text(String s, int x, int y, int c, int scale) {
        int cx = x;
        for (int k = 0; k < s.length(); k++) {
            String[] g = Icons.glyph(s.charAt(k));
            for (int j = 0; j < g.length; j++)
                for (int i = 0; i < g[j].length(); i++)
                    if (g[j].charAt(i) == '#') rect(cx + i * scale, y + j * scale, cx + i * scale + scale - 1, y + j * scale + scale - 1, c);
            cx += (g[0].length() + 1) * scale;
        }
    }

    static int textWidth(String s, int scale) {
        int w = 0;
        for (int k = 0; k < s.length(); k++) w += (Icons.glyph(s.charAt(k))[0].length() + 1) * scale;
        return w - scale;
    }
}
