package brewemu.doomrpg;

import brewemu.brew.Brew;
import brewemu.cpu.Memory;

/**
 * The player's weapons in Doom RPG (BREW), read from the emulated phone's memory: which weapons are owned,
 * the ammo, the weapon in hand. Choosing a weapon presses the game's own "next / previous weapon" keys until
 * the game holds it, so the game itself still decides (nothing in the game is changed). The data layout and
 * the weapon rules are the ones documented by the DoomRPG-RE project.
 */
public final class DoomWeapons {
    public static final int COUNT = 12;   // 0 axe ... 8 BFG, 9-11 dogs
    public static final String[] NAMES = {"AXE", "FIRE EXTINGUISHER", "PISTOL", "SHOTGUN", "CHAINGUN", "SUPER SHOTGUN",
            "PLASMA GUN", "ROCKET LAUNCHER", "BFG", "HELLHOUND", "CERBERUS", "DEMON WOLF"};
    public static final String[] AMMO_NAMES = {"HALON", "BULLETS", "SHELLS", "ROCKETS", "CELLS"};
    /** Ammo type and ammo used per shot, per weapon (as the game's weapon table). */
    public static final int[] AMMO_TYPE = {0, 0, 1, 2, 1, 2, 4, 3, 4, 5, 5, 5}, AMMO_USE = {0, 1, 1, 1, 3, 2, 3, 1, 15, 0, 0, 0};

    private static final int APP_CANVAS = 0x104, APP_PLAYER = 0x10c, CV_STATE = 0xa4, ST_PLAYING = 3;
    private static final int PL_AMMO = 25, PL_WEAPONS = 32, PL_WEAPON = 52;

    /** What was read. */
    public int owned, weapon = -1;
    public final int[] ammo = new int[6];

    /** Reads the weapons; false if the game isn't being played right now (or the data doesn't look right). */
    public boolean read(Brew b) {
        try {
            Memory m = b.mem;
            if (b.app == 0) return false;
            int canvas = m.read32(b.app + APP_CANVAS), player = m.read32(b.app + APP_PLAYER);
            if (canvas <= 0 || canvas >= m.size || player <= 0 || player >= m.size) return false;
            if (m.read32(canvas + CV_STATE) != ST_PLAYING) return false;
            int own = m.read32(player + PL_WEAPONS), w = m.read32(player + PL_WEAPON);
            if ((own & ~0xfff) != 0 || w < 0 || w >= COUNT) return false;
            owned = own; weapon = w;
            for (int i = 0; i < 6; i++) ammo[i] = m.read8(player + PL_AMMO + i);
            return true;
        } catch (RuntimeException e) { return false; }
    }

    public boolean owns(int w) { return (owned & (1 << w)) != 0; }

    /** The game lets you switch to an owned weapon that has ammo (or needs none). */
    public boolean usable(int w) { return owns(w) && (ammo[AMMO_TYPE[w]] > 0 || AMMO_USE[w] == 0); }

    /** The weapon "next weapon" would give (as the game picks it), or -1 if none. */
    public int next() {
        for (int i = weapon + 1; i < COUNT; i++) if (usable(i)) return i;
        for (int i = 0; i < weapon; i++) if (usable(i)) return i;
        return -1;
    }

    /** The weapon "previous weapon" would give, or -1 if none. */
    public int prev() {
        for (int i = weapon - 1; i >= 0; i--) if (usable(i)) return i;
        for (int i = COUNT - 1; i > weapon; i--) if (usable(i)) return i;
        return -1;
    }

    // switching in progress (the game takes the key on its next frame, so this goes one press per switch)
    private int target = -1, key, lastHeld, presses, waited;

    /**
     * Starts switching to weapon w with the game's own next / previous keys (the shorter way round).
     * Call {@link #tick} after every frame; it presses again each time the game has switched, until it holds w.
     * Runs on the emulator thread. Returns false if w can't be chosen now.
     */
    public boolean select(Brew b, int w) {
        target = -1;
        if (!read(b) || !usable(w)) return false;
        if (weapon == w) return true;
        int fwd = steps(w, true), back = steps(w, false);
        if (fwd > COUNT && back > COUNT) return false;
        key = fwd <= back ? Brew.AVK_0 + 7 : Brew.AVK_STAR;
        target = w; lastHeld = weapon; presses = 1; waited = 0;
        b.keyDown(key); b.keyUp(key);
        return true;
    }

    /** True while a switch is still going on. */
    public boolean switching() { return target >= 0; }

    /** Moves a switch along; call after every frame on the emulator thread. */
    public void tick(Brew b) {
        if (target < 0) return;
        int h = held(b);
        if (h < 0 || h == target) { target = -1; return; }
        if (h != lastHeld) {
            lastHeld = h; waited = 0;
            if (++presses > COUNT + 2) { target = -1; return; }
            b.keyDown(key); b.keyUp(key);
        } else if (++waited > 40) target = -1;   // the game didn't take it (busy): give up
    }

    private int steps(int w, boolean forward) {
        int cur = weapon, n = 0;
        while (cur != w) { cur = step(cur, forward); n++; if (cur < 0 || n > COUNT) return 99; }
        return n;
    }

    /** The weapon in hand, whatever the game is doing; -1 if it can't be read. */
    private static int held(Brew b) {
        try {
            int player = b.mem.read32(b.app + APP_PLAYER);
            if (player <= 0 || player >= b.mem.size) return -1;
            int w = b.mem.read32(player + PL_WEAPON);
            return w >= 0 && w < COUNT ? w : -1;
        } catch (RuntimeException e) { return -1; }
    }

    private int step(int from, boolean forward) {
        int keep = weapon; weapon = from;
        int r = forward ? next() : prev();
        weapon = keep;
        return r;
    }
}
