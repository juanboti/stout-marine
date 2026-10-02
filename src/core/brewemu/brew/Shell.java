package brewemu.brew;

import brewemu.cpu.Memory;

/** IShell: object creation, device info, timers and resource-file access. */
final class Shell extends BObj {
    Shell(Brew b) { super(b, Brew.K_SHELL, 16); refs = 1 << 20; }

    @Override public int invoke(int slot) {
        Memory m = b.mem;
        switch (slot) {
            case 0: return 2;
            case 1: return 1;    // the shell is never destroyed
            case 2: return createInstance(arg(1), arg(2));
            case 4: deviceInfo(arg(1)); return 0;
            case 6:  // CloseApplet
                b.host.log("CloseApplet");
                b.closed = true;
                b.host.closeApplet();
                return Brew.SUCCESS;
            case 11: b.setTimer(arg(1), arg(2), arg(3)); return Brew.SUCCESS;   // SetTimer(ms, pfn, pUser)
            case 12: b.cancelTimer(arg(1), arg(2)); return Brew.SUCCESS;        // CancelTimer(pfn, pUser)
            case 17: return loadResString(m.cstr(arg(1)), arg(2) & 0xffff, arg(3), arg(4));
            case 20: {  // FreeResData(pData)
                int p = arg(1);
                if (p != 0) b.heap.release(p);
                return 0;
            }
            case 28: {  // MessageBoxText(title, text): shown only in the log
                b.host.log("MessageBox: " + m.wstr(arg(1)) + " / " + m.wstr(arg(2)));
                return Brew.SUCCESS;
            }
            case 41: return loadResDataEx(m.cstr(arg(1)), arg(2) & 0xffff, arg(3), arg(4), arg(5));
            default: return super.invoke(slot);
        }
    }

    private int createInstance(int clsid, int pp) {
        BObj o;
        switch (clsid) {
            case Brew.CLS_DISPLAY: o = b.display; b.display.refs++; break;
            case Brew.CLS_HEAP: o = new HeapStat(b); break;
            case Brew.CLS_FILEMGR: o = new FileMgr(b); break;
            case Brew.CLS_SOUND: o = new Sound(b); break;
            case Brew.CLS_MEMASTREAM: o = new MemStream(b); break;
            case Brew.CLS_UNZIPSTREAM: o = new Unzip(b); break;
            case Brew.CLS_GRAPHICS: o = new Graphics(b); break;
            case Brew.CLS_MEDIA: o = new Media(b); break;
            default:
                b.host.log("CreateInstance: unsupported class " + Integer.toHexString(clsid));
                if (pp != 0) b.mem.write32(pp, 0);
                return Brew.ECLASSNOTSUPPORT;
        }
        if (pp != 0) b.mem.write32(pp, o.addr);
        return Brew.SUCCESS;
    }

    /** AEEDeviceInfo: screen size, colour depth, RAM and capability flags. */
    private void deviceInfo(int p) {
        Memory m = b.mem;
        int size = m.read32(p + 40);
        int limit = size >= 44 && size <= 256 ? size : 40;
        for (int i = 0; i < limit; i++) if (i < 40 || i >= 44) m.write8(p + i, 0);
        m.write16(p, b.screenW); m.write16(p + 2, b.screenH);
        m.write16(p + 14, 16);                 // colour depth
        m.write32(p + 16, Brew.HEAP_END - Brew.HEAP_BASE);
        m.write8(p + 21, 1);                   // vibrator
        m.write8(p + 25, 1);                   // MIDI
        m.write8(p + 26, 1);                   // CMX
        m.write16(p + 32, Brew.AVK_CLR);
    }

    private boolean ourBar(String name) {
        if (name == null) return false;
        String n = name.toLowerCase();
        int k = n.lastIndexOf('/');
        if (k >= 0) n = n.substring(k + 1);
        return n.equals(b.barName);
    }

    /** LoadResString(file, id, AECHAR* buf, int bytes): returns bytes written (0 = not found). */
    private int loadResString(String file, int id, int buf, int bytes) {
        String s = ourBar(file) ? b.bar.string(id) : null;
        if (s != null && b.stringOverrides.containsKey(id)) s = b.stringOverrides.get(id);
        if (s == null || buf == 0 || bytes < 2) return 0;
        int max = bytes / 2 - 1, n = Math.min(s.length(), max);
        for (int i = 0; i < n; i++) b.mem.write16(buf + 2 * i, s.charAt(i));
        b.mem.write16(buf + 2 * n, 0);
        return 2 * n + 2;
    }

    /**
     * LoadResDataEx(file, id, type, pBuf, pnSize): with pBuf == NULL the data is copied into newly allocated
     * memory (freed with FreeResData) and its size stored in *pnSize; otherwise the data is copied into pBuf.
     */
    private int loadResDataEx(String file, int id, int type, int pBuf, int pSize) {
        byte[] d = ourBar(file) ? b.bar.get(type, id) : null;
        if (d == null) { b.host.log("resource not found: " + file + " " + id + " type " + type); return 0; }
        Memory m = b.mem;
        if (pBuf == 0 || pBuf == -1) {
            int p = b.heap.alloc(d.length);
            if (p == 0) return 0;
            m.write(p, d, 0, d.length);
            if (pSize != 0) m.write32(pSize, d.length);
            return p;
        }
        int cap = pSize != 0 ? m.read32(pSize) : d.length;
        if (cap < d.length) { if (pSize != 0) m.write32(pSize, d.length); return 0; }
        m.write(pBuf, d, 0, d.length);
        if (pSize != 0) m.write32(pSize, d.length);
        return pBuf;
    }
}
