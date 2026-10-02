package brewemu.brew;

import brewemu.cpu.Memory;

/** IGraphics: simple shape drawing onto the device bitmap (lines, rectangles, circles) with a rectangular clip. */
final class Graphics extends BObj {
    private int color = 0, fillColor = 0, background = 0;   // 0xRRGGBB
    private boolean fillMode;
    private int cx0, cy0, cx1, cy1;                          // clip, exclusive max
    private boolean clip;

    Graphics(Brew b) { super(b, Brew.K_GRAPHICS, 16); }

    private Dib s() { return b.display.screen; }

    private void px(int x, int y, int rgb) {
        if (clip && (x < cx0 || y < cy0 || x >= cx1 || y >= cy1)) return;
        s().plot(x, y, s().fromRgb(rgb), Dib.RO_COPY);
    }

    private void hline(int x0, int x1, int y, int rgb) { for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) px(x, y, rgb); }

    private void line(int x0, int y0, int x1, int y1, int rgb) {
        int dx = Math.abs(x1 - x0), dy = -Math.abs(y1 - y0), sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1, err = dx + dy;
        for (int guard = 0; guard < 100000; guard++) {
            px(x0, y0, rgb);
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 >= dy) { err += dy; x0 += sx; }
            if (e2 <= dx) { err += dx; y0 += sy; }
        }
    }

    private void rect(int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) return;
        if (fillMode) for (int yy = y; yy < y + h; yy++) hline(x, x + w - 1, yy, fillColor);
        hline(x, x + w - 1, y, color); hline(x, x + w - 1, y + h - 1, color);
        for (int yy = y; yy < y + h; yy++) { px(x, yy, color); px(x + w - 1, yy, color); }
    }

    private void ellipse(int cx, int cy, int rx, int ry) {
        if (rx < 0 || ry < 0) return;
        if (fillMode) {
            for (int dy = -ry; dy <= ry; dy++) {
                int span = ry == 0 ? rx : (int) Math.floor(rx * Math.sqrt(1.0 - (double) dy * dy / ((double) ry * ry)) + 0.5);
                hline(cx - span, cx + span, cy + dy, fillColor);
            }
        }
        int steps = Math.max(16, 4 * (rx + ry));
        int px0 = cx + rx, py0 = cy;
        for (int i = 1; i <= steps; i++) {
            double t = 2 * Math.PI * i / steps;
            int x = (int) Math.round(cx + rx * Math.cos(t)), y = (int) Math.round(cy + ry * Math.sin(t));
            line(px0, py0, x, y, color);
            px0 = x; py0 = y;
        }
    }

    private static int rgb(int r, int g, int bl) { return (r & 255) << 16 | (g & 255) << 8 | (bl & 255); }

    @Override public int invoke(int slot) {
        Memory m = b.mem;
        switch (slot) {
            case 2: { int old = background; background = rgb(arg(1), arg(2), arg(3)); return Dib.toRgbval(old); }   // SetBackground
            case 4: { int old = color; color = rgb(arg(1), arg(2), arg(3)); return Dib.toRgbval(old); }             // SetColor
            case 6: { boolean old = fillMode; fillMode = arg(1) != 0; return old ? 1 : 0; }                          // SetFillMode
            case 7: return fillMode ? 1 : 0;                                                                        // GetFillMode
            case 8: { int old = fillColor; fillColor = rgb(arg(1), arg(2), arg(3)); return Dib.toRgbval(old); }     // SetFillColor
            case 12: {  // SetClip(AEEClip*, flags): type byte then AEERect at +4; NULL clears
                int p = arg(1);
                if (p == 0 || m.read8(p) == 0) { clip = false; return Brew.SUCCESS; }
                int x = (short) m.read16(p + 4), y = (short) m.read16(p + 6), w = (short) m.read16(p + 8), h = (short) m.read16(p + 10);
                cx0 = x; cy0 = y; cx1 = x + w; cy1 = y + h; clip = true;
                return Brew.SUCCESS;
            }
            case 21: {  // DrawLine(AEELine*)
                int p = arg(1);
                line((short) m.read16(p), (short) m.read16(p + 2), (short) m.read16(p + 4), (short) m.read16(p + 6), color);
                return Brew.SUCCESS;
            }
            case 22: {  // DrawRect(AEERect*)
                int p = arg(1);
                rect((short) m.read16(p), (short) m.read16(p + 2), (short) m.read16(p + 4), (short) m.read16(p + 6));
                return Brew.SUCCESS;
            }
            case 23: {  // DrawCircle(AEECircle*)
                int p = arg(1), r = (short) m.read16(p + 4);
                ellipse((short) m.read16(p), (short) m.read16(p + 2), r, r);
                return Brew.SUCCESS;
            }
            case 30: {  // ClearRect(AEERect*): fill with the background colour
                int p = arg(1);
                int x = (short) m.read16(p), y = (short) m.read16(p + 2), w = (short) m.read16(p + 4), h = (short) m.read16(p + 6);
                for (int yy = y; yy < y + h; yy++) hline(x, x + w - 1, yy, background);
                return Brew.SUCCESS;
            }
            case 32: b.display.update(); return 0;    // Update
            default: return super.invoke(slot);
        }
    }
}
