package brewemu.brew;

/** IMemAStream: a read stream over a guest buffer. */
final class MemStream extends BObj {
    int buf, size, pos, pfnFree, user;

    MemStream(Brew b) { super(b, Brew.K_MEMSTREAM, 16); }

    /** Remaining bytes as a host array. */
    byte[] remaining() {
        int n = Math.max(0, size - pos);
        byte[] out = new byte[n];
        if (n > 0) b.mem.read(buf + pos, out, 0, n);
        pos = size;
        return out;
    }

    @Override public int invoke(int slot) {
        switch (slot) {
            case 2: return 0;   // Readable(pfn, user): always readable (synchronous)
            case 3: {           // Read(buf, n)
                int dst = arg(1), n = Math.min(arg(2), size - pos);
                if (n <= 0) return 0;
                b.mem.copy(dst, buf + pos, n); pos += n;
                return n;
            }
            case 4: return 0;   // Cancel
            case 5: freeOld(); buf = arg(1); size = arg(2); pos = arg(3); pfnFree = 0; return 0;   // Set(buf, size, offset, bSysMem)
            case 6: freeOld(); buf = arg(1); size = arg(2); pos = arg(3); pfnFree = arg(4); user = arg(5); return 0;  // SetEx
            default: return super.invoke(slot);
        }
    }

    private void freeOld() {
        int f = pfnFree; pfnFree = 0;
        if (f != 0) b.call(f, user);
    }

    @Override protected void destroy() { freeOld(); }
}
