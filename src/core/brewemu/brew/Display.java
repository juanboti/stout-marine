package brewemu.brew;

import brewemu.cpu.Memory;

/** IDisplay: owns the 16-bit device bitmap and sends finished frames to the host. */
final class Display extends BObj {
    final Dib screen;
    private int[] frame;
    /** User colour items (RGBVAL): 2 = background, 3 = frame/line. */
    private final int[] colors = {0, 0, 0xFFFFFF00, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};

    Display(Brew b) {
        super(b, Brew.K_DISPLAY, 16);
        refs = 1 << 20;
        screen = new Dib(b, 16, b.screenW, b.screenH);
        screen.refs = 1 << 20;
    }

    private boolean pending;
    private byte[] shot;

    /**
     * IDISPLAY_Update: takes a copy of the screen now; it is converted and sent to the host once the current
     * callback returns (see {@link #flush}), so several updates in one callback cost one conversion.
     */
    void update() {
        int bytes = screen.pitch() * screen.height();
        if (shot == null || shot.length != bytes) shot = new byte[bytes];
        b.mem.read(screen.bits(), shot, 0, bytes);
        pending = true;
    }

    /** Sends the last updated frame to the host. */
    void flush() {
        if (!pending) return;
        pending = false;
        int w = screen.width(), h = screen.height(), pitch = screen.pitch();
        if (frame == null || frame.length != w * h) frame = new int[w * h];
        byte[] s = shot;
        for (int y = 0; y < h; y++) {
            int a = y * pitch, o = y * w;
            for (int x = 0; x < w; x++, a += 2) {
                int v = (s[a] & 0xff) | (s[a + 1] & 0xff) << 8;
                int r = (v >> 11) & 31, g = (v >> 5) & 63, bl = v & 31;
                frame[o + x] = 0xff000000 | (r << 3 | r >> 2) << 16 | (g << 2 | g >> 4) << 8 | (bl << 3 | bl >> 2);
            }
        }
        b.host.present(frame, w, h);
    }

    @Override public int invoke(int slot) {
        Memory m = b.mem;
        switch (slot) {
            case 0: return 2;
            case 1: return 1;
            case 5: {  // DrawRect(pRect, clrFrame, clrFill, flags)
                int r = arg(1), frameC = arg(2), fillC = arg(3), flags = arg(4);
                int x = 0, y = 0, w = screen.width(), h = screen.height();
                if (r != 0) { x = (short) m.read16(r); y = (short) m.read16(r + 2); w = (short) m.read16(r + 4); h = (short) m.read16(r + 6); }
                // RGB_NONE (0xFFFFFFFF) selects the user colours set with SetColor
                if (fillC == -1) fillC = colors[2];
                if (frameC == -1) frameC = colors[3];
                if ((flags & 2) != 0 && fillC != -1) screen.fill(x, y, w, h, screen.fromRgb(Dib.rgbval(fillC)), Dib.RO_COPY);
                if ((flags & 1) != 0 && frameC != -1) {
                    int v = screen.fromRgb(Dib.rgbval(frameC));
                    screen.fill(x, y, w, 1, v, Dib.RO_COPY); screen.fill(x, y + h - 1, w, 1, v, Dib.RO_COPY);
                    screen.fill(x, y, 1, h, v, Dib.RO_COPY); screen.fill(x + w - 1, y, 1, h, v, Dib.RO_COPY);
                }
                return 0;
            }
            case 7: update(); return 0;          // UpdateEx(bDefer)
            case 8: {  // BitBlt(xDst, yDst, cxDst, cyDst, pSrc, xSrc, ySrc, rop)
                BObj s = b.object(arg(5));
                if (s instanceof Dib) screen.blt(arg(1), arg(2), arg(3), arg(4), (Dib) s, arg(6), arg(7), arg(8));
                return 0;
            }
            case 10: {  // SetColor(item, rgb): returns the old colour
                int item = arg(1) & 15, old = colors[item];
                if (arg(2) != -1) colors[item] = arg(2);
                return old;
            }
            case 13: {  // CreateDIBitmap(ppDIB, depth, w, h)
                int pp = arg(1), depth = arg(2) & 0xff, w = arg(3) & 0xffff, h = arg(4) & 0xffff;
                if (depth != 1 && depth != 2 && depth != 4 && depth != 8 && depth != 16 && depth != 24 && depth != 32) return Brew.EBADPARM;
                Dib d = new Dib(b, depth, w, h);
                if (pp != 0) m.write32(pp, d.addr);
                return Brew.SUCCESS;
            }
            case 19: return Brew.SUCCESS;        // SetClipRect: the module only resets it; IDisplay drawing is not clipped here
            case 16: {  // GetDeviceBitmap(ppBmp)
                screen.refs++;
                if (arg(1) != 0) m.write32(arg(1), screen.addr);
                return Brew.SUCCESS;
            }
            default: return super.invoke(slot);
        }
    }
}
