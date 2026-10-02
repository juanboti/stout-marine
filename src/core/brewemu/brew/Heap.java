package brewemu.brew;

import brewemu.cpu.Memory;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/** First-fit allocator for guest memory [base, end). Blocks are 8-byte aligned; MALLOC memory is zero-filled. */
public final class Heap {
    private final Memory mem;
    public final int base, end;
    private final TreeMap<Integer, Integer> free = new TreeMap<Integer, Integer>();   // addr -> size
    private final HashMap<Integer, Integer> used = new HashMap<Integer, Integer>();   // addr -> size
    public int inUse, peak;

    public Heap(Memory mem, int base, int end) {
        this.mem = mem; this.base = base; this.end = end;
        free.put(base, end - base);
    }

    /** Returns 0 when out of memory. */
    public int alloc(int n) {
        if (n < 0) return 0;
        int size = (Math.max(n, 1) + 7) & ~7;
        for (Map.Entry<Integer, Integer> e : free.entrySet()) {
            int a = e.getKey(), s = e.getValue();
            if (s < size) continue;
            free.remove(a);
            if (s > size) free.put(a + size, s - size);
            used.put(a, size);
            inUse += size; if (inUse > peak) peak = inUse;
            mem.fill(a, 0, size);
            return a;
        }
        return 0;
    }

    public boolean isBlock(int a) { return used.containsKey(a); }

    public int sizeOf(int a) { Integer s = used.get(a); return s == null ? -1 : s; }

    public boolean release(int a) {
        Integer s = used.remove(a);
        if (s == null) return false;
        inUse -= s;
        int start = a, size = s;
        Map.Entry<Integer, Integer> lo = free.floorEntry(a);
        if (lo != null && lo.getKey() + lo.getValue() == a) { start = lo.getKey(); size += lo.getValue(); free.remove(lo.getKey()); }
        Integer hi = free.get(a + s);
        if (hi != null) { free.remove(a + s); size += hi; }
        free.put(start, size);
        return true;
    }

    /** realloc semantics; returns 0 on failure (old block kept). */
    public int realloc(int a, int n) {
        if (a == 0) return alloc(n);
        if (n == 0) { release(a); return 0; }
        int old = sizeOf(a);
        if (old < 0) return 0;
        if (n <= old) return a;
        int b = alloc(n);
        if (b == 0) return 0;
        mem.copy(b, a, old);
        release(a);
        return b;
    }

    public int freeBytes() { int t = 0; for (int s : free.values()) t += s; return t; }
}
