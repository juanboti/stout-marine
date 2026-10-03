package brewemu.brew;

import brewemu.cpu.Cpu;
import brewemu.cpu.Memory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;

/**
 * Minimal BREW runtime: loads an applet module (.mod) into emulated memory and provides the
 * subset of the BREW API that the module calls, implemented from the public API documentation.
 */
public final class Brew implements Cpu.TrapHandler {
    // ---- memory map
    public static final int MOD_BASE = 0x1000;
    public static final int MOD_LIMIT = 0x80000;
    public static final int HEAP_BASE = 0x100000;
    public static final int HEAP_END = 0x1100000;
    public static final int STACK_TOP = 0x1200000;
    public static final int RAM_SIZE = STACK_TOP;

    // ---- interface kinds (trap address = TRAP_BASE | kind << 12 | slot << 2)
    public static final int K_HELPER = 0, K_SHELL = 1, K_DISPLAY = 2, K_DIB = 3, K_GRAPHICS = 4, K_FILEMGR = 5,
            K_FILE = 6, K_MEMSTREAM = 7, K_UNZIP = 8, K_HEAP = 9, K_SOUND = 10, K_MEDIA = 11, K_GENERIC = 12;
    static final int SLOTS = 1024;
    /** -Dtrace=true logs every interface call (desktop debugging). */
    static final boolean TRACE = Boolean.getBoolean("trace");

    // ---- class ids
    public static final int CLS_DISPLAY = 0x1001001, CLS_HEAP = 0x1001002, CLS_FILEMGR = 0x1001003,
            CLS_SOUND = 0x100100b, CLS_MEMASTREAM = 0x100100c, CLS_UNZIPSTREAM = 0x1001014, CLS_DIB = 0x1001045,
            CLS_GRAPHICS = 0x1002001, CLS_MEDIA = 0x1005504;

    // ---- events
    public static final int EVT_APP_START = 0, EVT_APP_STOP = 1, EVT_APP_SUSPEND = 2, EVT_APP_RESUME = 3,
            EVT_KEY = 0x100, EVT_KEY_PRESS = 0x101, EVT_KEY_RELEASE = 0x102;

    // ---- BREW key codes (AVK_*)
    public static final int AVK_0 = 0xE021, AVK_STAR = 0xE02B, AVK_POUND = 0xE02C, AVK_CLR = 0xE030,
            AVK_UP = 0xE031, AVK_DOWN = 0xE032, AVK_LEFT = 0xE033, AVK_RIGHT = 0xE034, AVK_SELECT = 0xE035,
            AVK_SOFT1 = 0xE036, AVK_SOFT2 = 0xE037;

    public static final int SUCCESS = 0, EFAILED = 1, ENOMEMORY = 2, ECLASSNOTSUPPORT = 3, EBADPARM = 14,
            EUNSUPPORTED = 20;

    public interface Clock { long uptimeMs(); }

    public final Memory mem = new Memory(RAM_SIZE);
    public final Cpu cpu = new Cpu(mem);
    public final Heap heap = new Heap(mem, HEAP_BASE, HEAP_END);
    public final Host host;
    public final Bar bar;
    public final String barName;
    public Clock clock;
    public final int screenW, screenH;

    private final HashMap<Integer, BObj> objects = new HashMap<Integer, BObj>();
    private final int[] vtables = new int[16];
    private final HashSet<Long> reported = new HashSet<Long>();
    private final Random rnd = new Random();

    public Shell shell;
    public Display display;
    int helperTable;
    public int app;          // IApplet*
    int appClsid;
    public boolean closed;
    /** Host-side switch: when false, clips are not sent to the host (the module still sees normal playback). */
    public volatile boolean soundOn = true;
    final ArrayList<Media> medias = new ArrayList<Media>();
    /**
     * Replacement text for string resources of the game's .bar, by id. BREW games read their handset settings
     * (what the phone can do) from these strings; the host can describe the emulated phone here.
     */
    public final HashMap<Integer, String> stringOverrides = new HashMap<Integer, String>();

    /**
     * Doom RPG keeps "vibration can't be used together with sound" in string 7 ("1" on the handset this copy
     * was built for, which hides the game's own Vibrate option). This tells the game the phone can do both,
     * so its Options menu offers Vibrate. Nothing changes for other games or other string values.
     */
    // ---- Doom RPG: Vibrate starts on (once), afterwards the player's own choice is kept
    private boolean vibrateDefaultPending;
    /** Called (on the emulator thread) once the running game's Vibrate setting has been switched on. */
    public Runnable vibrateDefaultApplied;
    private static final String DOOMRPG_CONFIG = "Config";

    /**
     * Doom RPG keeps its Vibrate setting in its settings file ("Config"). Sets it to on if the file exists
     * (call before {@link #start}); returns false when there is no such file yet.
     */
    public boolean switchDoomRpgVibrateOnInFile(java.io.File dataDir) {
        if (!"0".equals(stringOverrides.get(7))) return false;
        java.io.File cfg = new java.io.File(dataDir, DOOMRPG_CONFIG);
        return cfg.isFile() && setConfigVibrate(cfg);
    }

    /**
     * For a fresh install (no settings file yet): switches the running game's Vibrate setting on as soon as the
     * game has set itself up; the game then saves it with its other settings. Call before {@link #start}.
     */
    public void switchDoomRpgVibrateOnAtStart() {
        if ("0".equals(stringOverrides.get(7))) vibrateDefaultPending = true;
    }

    /** Doom RPG's settings file: int version 0x16, then the Vibrate byte. */
    static boolean setConfigVibrate(java.io.File cfg) {
        try {
            java.io.RandomAccessFile f = new java.io.RandomAccessFile(cfg, "rw");
            try {
                if (f.length() < 5) return false;
                byte[] h = new byte[5];
                f.readFully(h);
                if (h[0] != 0x16 || h[1] != 0 || h[2] != 0 || h[3] != 0) return false;
                f.seek(4);
                f.write(1);
                return true;
            } finally { f.close(); }
        } catch (java.io.IOException e) {
            return false;
        }
    }

    /** Fresh install: switches the running game's Vibrate setting on once its canvas object exists. */
    private void applyPendingVibrate() {
        if (!vibrateDefaultPending || app == 0) return;
        // the game keeps the setting in its canvas object (applet +0x104), flag byte +0xB9
        int canvas = mem.read32(app + 0x104);
        if (canvas < HEAP_BASE || canvas >= HEAP_END - 0x100) return;
        vibrateDefaultPending = false;
        mem.write8(canvas + 0xb9, 1);
        if (vibrateDefaultApplied != null) vibrateDefaultApplied.run();
    }

    public boolean enableDoomRpgVibrateOption() {
        if (!"doomrpg.bar".equals(barName)) return false;
        if (!"1".equals(bar.string(7))) return false;
        stringOverrides.put(7, "0");
        return true;
    }
    public String fault;

    public Brew(Host host, byte[] mod, byte[] barData, String barName, int screenW, int screenH, int clsid) {
        this.host = host; this.bar = new Bar(barData); this.barName = barName;
        this.screenW = screenW; this.screenH = screenH; this.appClsid = clsid;
        if (mod.length > MOD_LIMIT - MOD_BASE) throw new IllegalArgumentException("module too large");
        mem.write(MOD_BASE, mod, 0, mod.length);
        cpu.traps = this;
        cpu.r[13] = STACK_TOP - 16;
        final long t0 = System.nanoTime();
        clock = new Clock() { public long uptimeMs() { return (System.nanoTime() - t0) / 1000000L; } };
        helperTable = heap.alloc(SLOTS * 4);
        for (int i = 0; i < SLOTS; i++) mem.write32(helperTable + 4 * i, trapAddr(K_HELPER, i));
        mem.write32(MOD_BASE - 4, helperTable);
        shell = new Shell(this);
        display = new Display(this);
    }

    // ------------------------------------------------------------------ objects & traps
    static int trapAddr(int kind, int slot) { return Cpu.TRAP_BASE | kind << 12 | slot << 2; }

    int vtable(int kind) {
        if (vtables[kind] == 0) {
            int vt = heap.alloc(SLOTS * 4);
            for (int i = 0; i < SLOTS; i++) mem.write32(vt + 4 * i, trapAddr(kind, i));
            vtables[kind] = vt;
        }
        return vtables[kind];
    }

    void register(BObj o) { objects.put(o.addr, o); }
    void unregister(BObj o) { objects.remove(o.addr); }
    public BObj object(int addr) { return objects.get(addr); }

    /** i-th argument of the current host call (r0-r3, then the stack). */
    public int arg(int i) { return i < 4 ? cpu.r[i] : mem.read32(cpu.r[13] + 4 * (i - 4)); }

    public void trap(Cpu c, int address) {
        int kind = (address >>> 12) & 0xffff, slot = (address & 0xfff) >>> 2;
        int ret;
        if (kind == K_HELPER) ret = helper(slot);
        else {
            BObj o = objects.get(c.r[0]);
            if (TRACE && o != null) host.log("call " + o.name() + "[" + slot + "] r1-3=" + Integer.toHexString(c.r[1]) + " "
                    + Integer.toHexString(c.r[2]) + " " + Integer.toHexString(c.r[3]) + " lr=" + Integer.toHexString(c.r[14]));
            if (o == null || o.kind != kind) {
                report(kind, slot, "call on unknown object " + Integer.toHexString(c.r[0]));
                ret = EFAILED;
            } else ret = o.invoke(slot);
        }
        c.r[0] = ret;
    }

    int unhandled(BObj o, int slot) {
        report(o.kind, slot, o.name() + "[" + slot + "]");
        return 0;
    }

    void report(int kind, int slot, String what) {
        long key = (long) kind << 32 | slot;
        if (!reported.add(key)) return;
        host.log(String.format("unhandled %s r0-3=%08x %08x %08x %08x sp+0=%08x sp+4=%08x lr=%08x", what,
                cpu.r[0], cpu.r[1], cpu.r[2], cpu.r[3], mem.read32(cpu.r[13]), mem.read32(cpu.r[13] + 4), cpu.r[14]));
    }

    // ------------------------------------------------------------------ guest calls
    public int call(int fn, int... args) {
        if (args.length <= 4) return cpu.call(fn, args, null);
        int[] reg = new int[4], st = new int[args.length - 4];
        System.arraycopy(args, 0, reg, 0, 4);
        System.arraycopy(args, 4, st, 0, st.length);
        return cpu.call(fn, reg, st);
    }

    /** Loads the module and creates the applet; returns false on failure. */
    public boolean start() {
        int pp = heap.alloc(4);
        int r = call(MOD_BASE, shell.addr, helperTable, pp);
        int mod = mem.read32(pp);
        if (r != 0 || mod == 0) { host.log("AEEMod_Load failed " + r); return false; }
        int vt = mem.read32(mod);
        mem.write32(pp, 0);
        r = call(mem.read32(vt + 8), mod, shell.addr, appClsid, pp);
        app = mem.read32(pp);
        heap.release(pp);
        if (r != 0 || app == 0) { host.log("CreateInstance(applet) failed " + r); return false; }
        int st = heap.alloc(32);
        mem.write32(st, 0); mem.write32(st + 4, appClsid); mem.write32(st + 8, display.addr);
        mem.write16(st + 12, 0); mem.write16(st + 14, 0); mem.write16(st + 16, screenW); mem.write16(st + 18, screenH);
        mem.write32(st + 20, 0);
        int ok = event(EVT_APP_START, 0, st);
        applyPendingVibrate();
        return ok != 0;
    }

    public int event(int evt, int w, int dw) {
        if (app == 0 || closed) return 0;
        int vt = mem.read32(app);
        int r = call(mem.read32(vt + 8), app, evt, w, dw);
        display.flush();
        return r;
    }

    public void keyDown(int avk) { event(EVT_KEY_PRESS, avk, 0); event(EVT_KEY, avk, 0); }
    public void keyUp(int avk) { event(EVT_KEY_RELEASE, avk, 0); }

    public void stop() {
        if (app == 0 || closed) return;
        event(EVT_APP_STOP, 0, 0);
        closed = true;
    }

    public void suspend() { event(EVT_APP_SUSPEND, 0, 0); }
    public void resume() {
        int st = heap.alloc(32);
        mem.write32(st + 4, appClsid); mem.write32(st + 8, display.addr);
        mem.write16(st + 16, screenW); mem.write16(st + 18, screenH);
        event(EVT_APP_RESUME, 0, st);
    }

    // ------------------------------------------------------------------ timers
    static final class Timer { int fn, user; long due; }
    final ArrayList<Timer> timers = new ArrayList<Timer>();

    void setTimer(int ms, int fn, int user) {
        cancelTimer(fn, user);
        Timer t = new Timer(); t.fn = fn; t.user = user; t.due = clock.uptimeMs() + Math.max(0, ms);
        timers.add(t);
    }

    void cancelTimer(int fn, int user) {
        for (int i = timers.size() - 1; i >= 0; i--) {
            Timer t = timers.get(i);
            if (t.user == user && (fn == 0 || t.fn == fn)) timers.remove(i);
        }
    }

    /** Next timer due time, or Long.MAX_VALUE. */
    public long nextDue() {
        long d = Long.MAX_VALUE;
        for (Timer t : timers) d = Math.min(d, t.due);
        return d;
    }

    /** Runs every timer that is due now and reports finished sounds; returns how many timers ran. */
    public int runTimers() {
        for (Media md : new ArrayList<Media>(medias)) if (!closed) md.poll();
        long now = clock.uptimeMs();
        int n = 0;
        while (!closed) {
            Timer best = null;
            for (Timer t : timers) if (t.due <= now && (best == null || t.due < best.due)) best = t;
            if (best == null) break;
            timers.remove(best);
            call(best.fn, best.user);
            display.flush();
            if (vibrateDefaultPending) applyPendingVibrate();
            n++;
        }
        return n;
    }

    // ------------------------------------------------------------------ helper functions (AEEStdLib)
    private int helper(int slot) {
        Memory m = mem;
        int a0 = cpu.r[0], a1 = cpu.r[1], a2 = cpu.r[2];
        switch (slot) {
            case 0: case 50:  // MEMMOVE / MEMCPY
                if (a2 > 0) m.copy(a0, a1, a2);
                return a0;
            case 1:  // MEMSET
                if (a2 > 0) m.fill(a0, a1, a2);
                return a0;
            case 2: { // STRCPY
                int n = m.cstrlen(a1); m.copy(a0, a1, n + 1); return a0;
            }
            case 3: { // STRCAT
                int d = a0 + m.cstrlen(a0); int n = m.cstrlen(a1); m.copy(d, a1, n + 1); return a0;
            }
            case 4: return strncmp(a0, a1, Integer.MAX_VALUE);   // STRCMP
            case 5: return m.cstrlen(a0);                        // STRLEN
            case 6: {  // STRCHR
                int c = a1 & 0xff;
                for (int p = a0; ; p++) { int x = m.read8(p); if (x == c) return p; if (x == 0) return 0; }
            }
            case 7: {  // STRRCHR
                int c = a1 & 0xff, last = 0;
                for (int p = a0; ; p++) { int x = m.read8(p); if (x == c) last = p; if (x == 0) return last; }
            }
            case 8: {  // SPRINTF(dst, fmt, ...)
                String s = Fmt.format(m, m.cstr(a1), varargs(2, 0));
                putCstr(a0, s, Integer.MAX_VALUE); return s.length();
            }
            case 9: { int n = m.wstrlen(a1); m.copy(a0, a1, 2 * n + 2); return a0; }              // WSTRCPY
            case 10: { int d = a0 + 2 * m.wstrlen(a0); int n = m.wstrlen(a1); m.copy(d, a1, 2 * n + 2); return a0; } // WSTRCAT
            case 11: { String x = m.wstr(a0), y = m.wstr(a1); return Integer.signum(x.compareTo(y)); }   // WSTRCMP
            case 12: return m.wstrlen(a0);                       // WSTRLEN
            case 16: {  // STRTOWSTR(src, dst, nbytes)
                if (a2 < 2) return a1;
                String s = m.cstr(a0); int max = a2 / 2 - 1;
                int n = Math.min(s.length(), max);
                for (int i = 0; i < n; i++) m.write16(a1 + 2 * i, s.charAt(i));
                m.write16(a1 + 2 * n, 0);
                return a1;
            }
            case 17: {  // WSTRTOSTR(src, dst, nbytes)
                if (a2 < 1) return a1;
                String s = m.wstr(a0); int n = Math.min(s.length(), a2 - 1);
                for (int i = 0; i < n; i++) m.write8(a1 + i, s.charAt(i) < 256 ? s.charAt(i) : '?');
                m.write8(a1 + n, 0);
                return a1;
            }
            case 26: return heap.alloc(a0);                      // MALLOC (zeroed)
            case 27: if (a0 != 0 && !heap.release(a0)) host.log("FREE of non-heap pointer " + Integer.toHexString(a0)); return 0;
            case 36: return atoi(m.cstr(a0));                    // ATOI
            case 39: host.log("DBG " + Fmt.format(m, m.cstr(a0), varargs(1, 0))); return 0;   // DBGPRINTF
            case 42: for (int i = 0; i < a1; i++) m.write8(a0 + i, rnd.nextInt(256)); return 0; // GETRAND
            case 43: return getTimeMs();                         // GETTIMEMS: milliseconds since local midnight
            case 44: return (int) clock.uptimeMs();             // GETUPTIMEMS
            case 45: return (int) (System.currentTimeMillis() / 1000L - 315964800L
                    + java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 1000);   // GETTIMESECONDS
            case 48: return app;                                 // GETAPPINSTANCE
            case 51: return strncmp(a0, a1, a2);                 // STRNCMP
            case 80: {  // VSNPRINTF(buf, size, fmt, va_list)
                String s = Fmt.format(m, m.cstr(a2), varargs(-1, cpu.r[3]));
                if (a1 > 0) putCstr(a0, s, a1);
                return s.length();
            }
            case 81: {  // SNPRINTF(buf, size, fmt, ...)
                String s = Fmt.format(m, m.cstr(a2), varargs(3, 0));
                if (a1 > 0) putCstr(a0, s, a1);
                return s.length();
            }
            default:
                report(K_HELPER, slot, "helper " + slot);
                return 0;
        }
    }

    /**
     * GETTIMEMS: time of day in milliseconds (local time). It moves on with the emulator's clock from the time of
     * day at the first call, so it pauses with the game. Doom RPG uses it to animate fires and other scenery.
     */
    private long dayMsBase = Long.MIN_VALUE;
    private int getTimeMs() {
        final long day = 86400000L;
        if (dayMsBase == Long.MIN_VALUE) {
            long now = System.currentTimeMillis();
            dayMsBase = (now + java.util.TimeZone.getDefault().getOffset(now)) % day - clock.uptimeMs();
        }
        return (int) (((dayMsBase + clock.uptimeMs()) % day + day) % day);
    }

    private int strncmp(int a, int b2, int n) {
        for (int i = 0; i < n; i++) {
            int x = mem.read8(a + i), y = mem.read8(b2 + i);
            if (x != y) return x - y;
            if (x == 0) return 0;
        }
        return 0;
    }

    private static int atoi(String s) {
        int i = 0, n = s.length();
        while (i < n && Character.isWhitespace(s.charAt(i))) i++;
        boolean neg = false;
        if (i < n && (s.charAt(i) == '-' || s.charAt(i) == '+')) { neg = s.charAt(i) == '-'; i++; }
        int v = 0;
        while (i < n && Character.isDigit(s.charAt(i))) { v = v * 10 + (s.charAt(i) - '0'); i++; }
        return neg ? -v : v;
    }

    /** Writes a zero-terminated byte string, truncated to fit `size` bytes including the terminator. */
    void putCstr(int dst, String s, int size) {
        int n = Math.min(s.length(), size - 1);
        for (int i = 0; i < n; i++) mem.write8(dst + i, s.charAt(i));
        mem.write8(dst + n, 0);
    }

    /** Variadic argument source: from register index `first` then the caller's stack, or from a va_list pointer. */
    private Fmt.Args varargs(final int first, final int vaList) {
        final int sp = cpu.r[13];
        final int[] regs = cpu.r.clone();
        return new Fmt.Args() {
            int i = first, k = 0;
            public int next() {
                if (first < 0) return mem.read32(vaList + 4 * k++);
                if (i < 4) return regs[i++];
                return mem.read32(sp + 4 * k++);
            }
        };
    }
}
