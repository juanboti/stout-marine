package brewemu.brew;

/** IHeap: only the memory statistics are used. */
final class HeapStat extends BObj {
    HeapStat(Brew b) { super(b, Brew.K_HEAP, 16); }

    @Override public int invoke(int slot) {
        switch (slot) {
            case 7: return b.heap.inUse;      // GetMemStats: bytes in use
            default: return super.invoke(slot);
        }
    }
}
