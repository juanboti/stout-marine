package brewemu.brew;

/**
 * A BREW interface instance implemented on the host. The guest sees a struct whose first word points at a
 * vtable of trap addresses; calls land in {@link #invoke} with the arguments still in the CPU registers.
 */
public abstract class BObj {
    public final Brew b;
    public final int kind;
    /** Guest address of the object struct. */
    public final int addr;
    int refs = 1;
    boolean dead;

    protected BObj(Brew b, int kind, int structBytes) {
        this.b = b; this.kind = kind;
        addr = b.heap.alloc(Math.max(structBytes, 8));
        if (addr == 0) throw new IllegalStateException("guest heap exhausted");
        b.mem.write32(addr, b.vtable(kind));
        b.register(this);
    }

    /** Handles vtable slot `slot`; returns the value for r0. Slots 0/1 are AddRef/Release by default. */
    public int invoke(int slot) {
        if (slot == 0) return ++refs;
        if (slot == 1) return release();
        return b.unhandled(this, slot);
    }

    public int release() {
        if (dead) return 0;
        if (--refs > 0) return refs;
        dead = true;
        destroy();
        b.unregister(this);
        b.heap.release(addr);
        return 0;
    }

    /** Frees host/guest resources owned by this object. */
    protected void destroy() {}

    public String name() { return getClass().getSimpleName(); }

    // short-hands
    protected int arg(int i) { return b.arg(i); }
}
