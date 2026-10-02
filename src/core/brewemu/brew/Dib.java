package brewemu.brew;

import brewemu.cpu.Memory;

/**
 * IBitmap / IDIB. The pixel data and header live in guest memory (the module reads and writes them directly):
 * +0 vtbl, +4 pPaletteMap, +8 pBmp, +0xC pRGB, +0x10 ncTransparent, +0x14 cx, +0x16 cy, +0x18 nPitch,
 * +0x1A cntRGB, +0x1C nDepth, +0x1D nColorScheme. Palette entries are 0x00RRGGBB.
 */
final class Dib extends BObj {
    static final int SCHEME_565 = 16;
    // raster operations
    static final int RO_OR = 0, RO_XOR = 1, RO_COPY = 2, RO_NOT = 3, RO_TRANSPARENT = 7;
    /** The module uses COPY (2) and 7 for colour-keyed images; 6 is treated the same way. */
    static boolean keyed(int rop) { return rop == 6 || rop == 7; }

    private int ownBits;   // pixel buffer allocated by us (freed on release)

    /** New DIB with its own pixel buffer. */
    Dib(Brew b, int depth, int w, int h) {
        super(b, Brew.K_DIB, 0x24);
        Memory m = b.mem;
        int pitch = ((w * depth + 31) / 32) * 4;
        ownBits = b.heap.alloc(Math.max(4, pitch * h));
        if (ownBits == 0) throw new IllegalStateException("guest heap exhausted");
        m.write32(addr + 8, ownBits);
        m.write32(addr + 0xc, 0);
        m.write32(addr + 0x10, 0);
        m.write16(addr + 0x14, w); m.write16(addr + 0x16, h);
        m.write16(addr + 0x18, pitch);
        m.write16(addr + 0x1a, 0);
        m.write8(addr + 0x1c, depth);
        m.write8(addr + 0x1d, depth == 16 ? SCHEME_565 : 0);
    }

    int bits() { return b.mem.read32(addr + 8); }
    int palette() { return b.mem.read32(addr + 0xc); }
    int transparent() { return b.mem.read32(addr + 0x10); }
    int width() { return b.mem.read16(addr + 0x14); }
    int height() { return b.mem.read16(addr + 0x16); }
    int pitch() { return (short) b.mem.read16(addr + 0x18); }
    int depth() { return b.mem.read8(addr + 0x1c); }

    @Override protected void destroy() {
        if (ownBits != 0) b.heap.release(ownBits);
        ownBits = 0;
    }

    // ------------------------------------------------------------------ pixel access (native values)
    int get(int x, int y) {
        Memory m = b.mem;
        int row = bits() + y * pitch();
        switch (depth()) {
            case 16: return m.read16(row + 2 * x);
            case 8: return m.read8(row + x);
            case 4: { int v = m.read8(row + (x >> 1)); return (x & 1) == 0 ? v >> 4 : v & 15; }
            case 2: { int v = m.read8(row + (x >> 2)); return (v >> (6 - 2 * (x & 3))) & 3; }
            case 1: { int v = m.read8(row + (x >> 3)); return (v >> (7 - (x & 7))) & 1; }
            case 24: return m.read8(row + 3 * x) | m.read8(row + 3 * x + 1) << 8 | m.read8(row + 3 * x + 2) << 16;
            case 32: return m.read32(row + 4 * x) & 0xffffff;
            default: return 0;
        }
    }

    void set(int x, int y, int v) {
        Memory m = b.mem;
        int row = bits() + y * pitch();
        switch (depth()) {
            case 16: m.write16(row + 2 * x, v); break;
            case 8: m.write8(row + x, v); break;
            case 4: { int a = row + (x >> 1), o = m.read8(a); m.write8(a, (x & 1) == 0 ? (o & 0x0f) | (v & 15) << 4 : (o & 0xf0) | (v & 15)); break; }
            case 2: { int a = row + (x >> 2), s = 6 - 2 * (x & 3), o = m.read8(a); m.write8(a, (o & ~(3 << s)) | (v & 3) << s); break; }
            case 1: { int a = row + (x >> 3), s = 7 - (x & 7), o = m.read8(a); m.write8(a, (o & ~(1 << s)) | (v & 1) << s); break; }
            case 24: m.write8(row + 3 * x, v); m.write8(row + 3 * x + 1, v >> 8); m.write8(row + 3 * x + 2, v >> 16); break;
            case 32: m.write32(row + 4 * x, v); break;
            default: break;
        }
    }

    /** Native pixel value to 0xRRGGBB. */
    int toRgb(int v) {
        int d = depth();
        if (d == 16) {
            int r = (v >> 11) & 31, g = (v >> 5) & 63, bl = v & 31;
            return (r << 3 | r >> 2) << 16 | (g << 2 | g >> 4) << 8 | (bl << 3 | bl >> 2);
        }
        if (d >= 24) return v & 0xffffff;
        int pal = palette(), n = b.mem.read16(addr + 0x1a);
        if (pal != 0 && v < n) return b.mem.read32(pal + 4 * v) & 0xffffff;
        int lvl = v * 255 / ((1 << d) - 1);
        return lvl * 0x010101;
    }

    /** 0xRRGGBB to a native pixel value. */
    int fromRgb(int rgb) {
        int d = depth();
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, bl = rgb & 255;
        if (d == 16) return (r >> 3) << 11 | (g >> 2) << 5 | (bl >> 3);
        if (d >= 24) return rgb & 0xffffff;
        int pal = palette(), n = b.mem.read16(addr + 0x1a), best = 0, bestD = Integer.MAX_VALUE;
        if (pal == 0 || n == 0) return ((r + g + bl) / 3) * ((1 << d) - 1) / 255;
        for (int i = 0; i < n; i++) {
            int c = b.mem.read32(pal + 4 * i);
            int dr = ((c >> 16) & 255) - r, dg = ((c >> 8) & 255) - g, db = (c & 255) - bl;
            int dist = dr * dr + dg * dg + db * db;
            if (dist < bestD) { bestD = dist; best = i; }
        }
        return best;
    }

    /** BREW RGBVAL (0xBBGGRR00) to 0xRRGGBB. */
    static int rgbval(int c) { return ((c >>> 8) & 255) << 16 | ((c >>> 16) & 255) << 8 | ((c >>> 24) & 255); }
    static int toRgbval(int rgb) { return (rgb & 255) << 24 | ((rgb >> 8) & 255) << 16 | ((rgb >> 16) & 255) << 8; }

    void plot(int x, int y, int v, int rop) {
        if (x < 0 || y < 0 || x >= width() || y >= height()) return;
        if (keyed(rop) && v == transparent()) return;   // colour-keyed: the transparent colour is not drawn
        switch (rop) {
            case RO_XOR: set(x, y, get(x, y) ^ v); break;
            case RO_OR: set(x, y, get(x, y) | v); break;
            case RO_NOT: set(x, y, ~v); break;
            default: set(x, y, v); break;
        }
    }

    void fill(int x, int y, int w, int h, int v, int rop) {
        if (keyed(rop) && v == transparent()) return;
        int x0 = Math.max(0, x), y0 = Math.max(0, y), x1 = Math.min(width(), x + w), y1 = Math.min(height(), y + h);
        if (depth() == 16 && rop == RO_COPY) {
            Memory m = b.mem;
            int bitsA = bits(), p = pitch();
            for (int yy = y0; yy < y1; yy++) for (int xx = x0; xx < x1; xx++) m.write16(bitsA + yy * p + 2 * xx, v);
            return;
        }
        for (int yy = y0; yy < y1; yy++) for (int xx = x0; xx < x1; xx++) plot(xx, yy, v, rop);
    }

    /** Copies src(xs,ys,w,h) to this(xd,yd) with colour conversion and transparency. */
    void blt(int xd, int yd, int w, int h, Dib src, int xs, int ys, int rop) {
        if (xd < 0) { xs -= xd; w += xd; xd = 0; }
        if (yd < 0) { ys -= yd; h += yd; yd = 0; }
        if (xs < 0) { xd -= xs; w += xs; xs = 0; }
        if (ys < 0) { yd -= ys; h += ys; ys = 0; }
        w = Math.min(w, Math.min(width() - xd, src.width() - xs));
        h = Math.min(h, Math.min(height() - yd, src.height() - ys));
        if (w <= 0 || h <= 0) return;
        boolean same = src.depth() == depth() && src.palette() == palette();
        int tr = src.transparent();
        int[] cache = null;
        if (!same && src.depth() <= 8) {
            cache = new int[1 << src.depth()];
            for (int i = 0; i < cache.length; i++) cache[i] = fromRgb(src.toRgb(i));
        }
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int v = src.get(xs + x, ys + y);
                if (keyed(rop) && v == tr) continue;
                int nv = same ? v : cache != null ? cache[v] : fromRgb(src.toRgb(v));
                plot(xd + x, yd + y, nv, keyed(rop) ? RO_COPY : rop);
            }
        }
    }

    /** Converts the whole bitmap to ARGB. */
    void toArgb(int[] out) {
        int w = width(), h = height();
        Memory m = b.mem;
        if (depth() == 16) {
            byte[] ram = m.ram;
            int base = bits(), p = pitch();
            for (int y = 0; y < h; y++) {
                int a = base + y * p, o = y * w;
                for (int x = 0; x < w; x++, a += 2) {
                    int v = (ram[a] & 0xff) | (ram[a + 1] & 0xff) << 8;
                    int r = (v >> 11) & 31, g = (v >> 5) & 63, bl = v & 31;
                    out[o + x] = 0xff000000 | (r << 3 | r >> 2) << 16 | (g << 2 | g >> 4) << 8 | (bl << 3 | bl >> 2);
                }
            }
            return;
        }
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) out[y * w + x] = 0xff000000 | toRgb(get(x, y));
    }

    // ------------------------------------------------------------------ IBitmap methods
    @Override public int invoke(int slot) {
        Memory m = b.mem;
        switch (slot) {
            case 2: {  // QueryInterface(clsid, ppo)
                int cls = arg(1), pp = arg(2);
                if (cls == Brew.CLS_DIB || cls == 0x01001048 /* IBitmap */ || cls == 0x01001046) {
                    refs++; if (pp != 0) m.write32(pp, addr); return Brew.SUCCESS;
                }
                b.host.log("IBitmap.QueryInterface " + Integer.toHexString(cls));
                if (pp != 0) m.write32(pp, 0);
                return Brew.ECLASSNOTSUPPORT;
            }
            case 3: return fromRgb(rgbval(arg(1)));                 // RGBToNative
            case 4: return toRgbval(toRgb(arg(1)));                 // NativeToRGB
            case 5: plot(arg(1), arg(2), arg(3), arg(4)); return Brew.SUCCESS;   // DrawPixel
            case 6: {  // GetPixel
                int x = arg(1), y = arg(2);
                if (x < 0 || y < 0 || x >= width() || y >= height()) return Brew.EBADPARM;
                if (arg(3) != 0) m.write32(arg(3), get(x, y));
                return Brew.SUCCESS;
            }
            case 7: {  // SetPixels(cnt, AEEPoint*, color, rop)
                int n = arg(1), pts = arg(2);
                for (int i = 0; i < n; i++) plot((short) m.read16(pts + 4 * i), (short) m.read16(pts + 4 * i + 2), arg(3), arg(4));
                return Brew.SUCCESS;
            }
            case 8: {  // DrawHScanline(y, xMin, xMax, color, rop)
                int y = arg(1), x0 = arg(2), x1 = arg(3);
                fill(x0, y, x1 - x0 + 1, 1, arg(4), arg(5));
                return Brew.SUCCESS;
            }
            case 9: {  // FillRect(rect, color, rop)
                int r = arg(1);
                fill((short) m.read16(r), (short) m.read16(r + 2), (short) m.read16(r + 4), (short) m.read16(r + 6), arg(2), arg(3));
                return Brew.SUCCESS;
            }
            case 10: {  // BltIn(xDst, yDst, dx, dy, pSrc, xSrc, ySrc, rop)
                BObj s = b.object(arg(5));
                if (!(s instanceof Dib)) return Brew.EBADPARM;
                blt(arg(1), arg(2), arg(3), arg(4), (Dib) s, arg(6), arg(7), arg(8));
                return Brew.SUCCESS;
            }
            case 11: {  // BltOut(xDst, yDst, dx, dy, pDst, xSrc, ySrc, rop)
                BObj d = b.object(arg(5));
                if (!(d instanceof Dib)) return Brew.EBADPARM;
                ((Dib) d).blt(arg(1), arg(2), arg(3), arg(4), this, arg(6), arg(7), arg(8));
                return Brew.SUCCESS;
            }
            case 12: {  // GetInfo(AEEBitmapInfo*, size)
                int p = arg(1);
                if (p != 0) { m.write32(p, width()); m.write32(p + 4, height()); m.write32(p + 8, depth()); }
                return Brew.SUCCESS;
            }
            case 13: {  // CreateCompatibleBitmap(pp, w, h)
                Dib d = new Dib(b, depth(), arg(2) & 0xffff, arg(3) & 0xffff);
                int pal = palette();
                if (pal != 0) { m.write32(d.addr + 0xc, pal); m.write16(d.addr + 0x1a, m.read16(addr + 0x1a)); }
                m.write8(d.addr + 0x1d, m.read8(addr + 0x1d));
                if (arg(1) != 0) m.write32(arg(1), d.addr);
                return Brew.SUCCESS;
            }
            case 14: m.write32(addr + 0x10, arg(1)); return Brew.SUCCESS;          // SetTransparencyColor
            case 15: if (arg(1) != 0) m.write32(arg(1), transparent()); return Brew.SUCCESS;
            default: return super.invoke(slot);
        }
    }
}
