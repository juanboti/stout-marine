package brewemu.brew;

import java.io.IOException;
import java.io.RandomAccessFile;

/** IFile backed by a host RandomAccessFile. */
final class BFile extends BObj {
    final RandomAccessFile raf;
    /** The host file (for diagnostics). */
    final java.io.File file;
    private final byte[] tmp = new byte[8192];

    BFile(Brew b, RandomAccessFile raf, java.io.File file) { super(b, Brew.K_FILE, 16); this.raf = raf; this.file = file; }

    @Override public int invoke(int slot) {
        try {
            switch (slot) {
                case 2: return 0;   // Readable
                case 3: {           // Read(buf, n)
                    int dst = arg(1), n = arg(2), done = 0;
                    while (done < n) {
                        int k = raf.read(tmp, 0, Math.min(tmp.length, n - done));
                        if (k <= 0) break;
                        b.mem.write(dst + done, tmp, 0, k); done += k;
                    }
                    return done;
                }
                case 4: return 0;   // Cancel
                case 5: {           // Write(buf, n)
                    int src = arg(1), n = arg(2), done = 0;
                    while (done < n) {
                        int k = Math.min(tmp.length, n - done);
                        b.mem.read(src + done, tmp, 0, k);
                        raf.write(tmp, 0, k); done += k;
                    }
                    return done;
                }
                case 6: {           // GetInfo(FileInfo*)
                    int p = arg(1);
                    if (p != 0) { b.mem.write8(p, 0); b.mem.write32(p + 8, (int) raf.length()); }
                    return Brew.SUCCESS;
                }
                case 7: {           // Seek(type, pos): 0 start, 1 end, 2 current
                    int type = arg(1), pos = arg(2);
                    long base = type == 1 ? raf.length() : type == 2 ? raf.getFilePointer() : 0;
                    long np = base + pos;
                    if (np < 0) return Brew.EFAILED;
                    raf.seek(np);
                    return Brew.SUCCESS;
                }
                case 8: raf.setLength(arg(1) & 0xffffffffL); return Brew.SUCCESS;   // Truncate
                default: return super.invoke(slot);
            }
        } catch (IOException e) {
            b.host.log("file error: " + e);
            return slot == 3 || slot == 5 ? 0 : Brew.EFAILED;
        }
    }

    @Override protected void destroy() {
        try { raf.close(); } catch (IOException e) { /* ignore */ }
    }
}
