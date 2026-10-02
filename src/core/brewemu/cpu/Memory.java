package brewemu.cpu;

/** Flat little-endian RAM starting at address 0. Accesses outside it raise {@link Fault}. */
public final class Memory {
    public final byte[] ram;
    public final int size;

    /** Counts unaligned guest accesses (diagnostics only). */
    public int unaligned;

    public Memory(int size) { this.size = size; ram = new byte[size]; }

    private int check(int a, int n) {
        if (a < 0 || a > size - n) throw new Fault(String.format("bad memory access %08x (size %d)", a, n));
        return a;
    }

    public int read8(int a) { return ram[check(a, 1)] & 0xff; }

    public int read16(int a) {
        if ((a & 1) != 0) unaligned++;
        a = check(a & ~1, 2);
        return (ram[a] & 0xff) | (ram[a + 1] & 0xff) << 8;
    }

    /** Aligned 32-bit read (address low bits ignored). */
    public int read32(int a) {
        if ((a & 3) != 0) unaligned++;
        a = check(a & ~3, 4);
        return (ram[a] & 0xff) | (ram[a + 1] & 0xff) << 8 | (ram[a + 2] & 0xff) << 16 | (ram[a + 3] & 0xff) << 24;
    }

    public void write8(int a, int v) { ram[check(a, 1)] = (byte) v; }

    public void write16(int a, int v) {
        if ((a & 1) != 0) unaligned++;
        a = check(a & ~1, 2);
        ram[a] = (byte) v; ram[a + 1] = (byte) (v >> 8);
    }

    public void write32(int a, int v) {
        if ((a & 3) != 0) unaligned++;
        a = check(a & ~3, 4);
        ram[a] = (byte) v; ram[a + 1] = (byte) (v >> 8); ram[a + 2] = (byte) (v >> 16); ram[a + 3] = (byte) (v >> 24);
    }

    /** Unaligned 32-bit read for host code (no rotation). */
    public int read32u(int a) {
        check(a, 4);
        return (ram[a] & 0xff) | (ram[a + 1] & 0xff) << 8 | (ram[a + 2] & 0xff) << 16 | (ram[a + 3] & 0xff) << 24;
    }

    public void write32u(int a, int v) {
        check(a, 4);
        ram[a] = (byte) v; ram[a + 1] = (byte) (v >> 8); ram[a + 2] = (byte) (v >> 16); ram[a + 3] = (byte) (v >> 24);
    }

    public void read(int a, byte[] dst, int off, int n) { System.arraycopy(ram, check(a, n), dst, off, n); }
    public void write(int a, byte[] src, int off, int n) { System.arraycopy(src, off, ram, check(a, n), n); }
    public void fill(int a, int v, int n) { java.util.Arrays.fill(ram, check(a, n), a + n, (byte) v); }
    public void copy(int dst, int src, int n) { check(dst, n); check(src, n); System.arraycopy(ram, src, ram, dst, n); }

    /** Zero-terminated byte string. */
    public String cstr(int a) {
        if (a == 0) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = a; i < size && ram[i] != 0; i++) sb.append((char) (ram[i] & 0xff));
        return sb.toString();
    }

    public int cstrlen(int a) { int i = a; while (ram[check(i, 1)] != 0) i++; return i - a; }

    /** Zero-terminated 16-bit (AECHAR) string. */
    public String wstr(int a) {
        if (a == 0) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = a; ; i += 2) { int c = read16(i); if (c == 0) break; sb.append((char) c); }
        return sb.toString();
    }

    public int wstrlen(int a) { int n = 0; while (read16(a + 2 * n) != 0) n++; return n; }

    public static final class Fault extends RuntimeException {
        public Fault(String m) { super(m); }
    }
}
