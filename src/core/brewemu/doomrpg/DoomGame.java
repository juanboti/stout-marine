package brewemu.doomrpg;

import brewemu.brew.Brew;
import brewemu.cpu.Memory;

/**
 * Doom RPG (BREW) helpers that read the emulated phone's memory or press the game's own keys:
 * the player's stats (the same numbers as the game's own Status screen) and "save now" (the game's own
 * menu, Save Game). Nothing in the game is changed. Layouts as documented by the DoomRPG-RE project.
 */
public final class DoomGame {
    private static final int APP_CANVAS = 0x104, APP_RENDER = 0x108, APP_PLAYER = 0x10c, APP_GAME = 0x110, APP_MENUSYS = 0x124;
    private static final int CV_STATE = 0xa4, ST_MENU = 2, ST_PLAYING = 3;
    // menu system: the shown items (32 bytes each: text[18], value[8], flags, action), the count, the cursor
    private static final int MS_ITEMS = 0x54, MS_NUM = 0xc54, MS_SEL = 0xc64, ITEM = 32;

    /** Stats, as the game's Status screen shows them. */
    public static final class Stats {
        public int health, maxHealth, armor, maxArmor, credits, level, xp, nextXp, defense, strength, agility, accuracy;
        public int sectorMs, totalMs, moves, totalMoves, deaths, xpGained, secrets, secretsTotal, monsters, monstersTotal;
    }

    /** Reads the stats; null if the game isn't being played (or the data doesn't look right). */
    public static Stats stats(Brew b, long upTimeMs) {
        try {
            Memory m = b.mem;
            int canvas = m.read32(b.app + APP_CANVAS), p = m.read32(b.app + APP_PLAYER), render = m.read32(b.app + APP_RENDER), game = m.read32(b.app + APP_GAME);
            if (!ok(m, canvas) || !ok(m, p) || !ok(m, render) || !ok(m, game)) return null;
            int st = m.read32(canvas + CV_STATE);
            if (st != ST_PLAYING && st != ST_MENU) return null;
            Stats s = new Stats();
            int p1 = m.read32(p + 64), p2 = m.read32(p + 68);
            s.health = p1 & 255; s.maxHealth = (p1 >> 8) & 255; s.armor = (p1 >> 16) & 255; s.maxArmor = (p1 >>> 24);
            s.defense = p2 & 255; s.strength = (p2 >> 8) & 255; s.agility = (p2 >> 16) & 255; s.accuracy = p2 >>> 24;
            s.level = m.read32(p + 40); s.xp = m.read32(p + 44); s.nextXp = m.read32(p + 48); s.credits = m.read32(p + 56);
            s.sectorMs = (int) (upTimeMs - m.read32(p + 92)); s.totalMs = m.read32(p + 96) + s.sectorMs;
            s.moves = m.read32(p + 100); s.totalMoves = m.read32(p + 104) + s.moves;
            s.xpGained = m.read32(p + 120); s.deaths = m.read32(p + 124);
            if (s.maxHealth == 0 || s.level < 0 || s.level > 99) return null;
            // secrets: lines marked secret (8), found (64)
            int la = m.read32(render + 0x2c), ln = m.read32(render + 0x30);
            if (ln > 0 && ln < 4096 && ok(m, la)) for (int i = 0; i < ln; i++) {
                int f = m.read32(la + i * 32 + 28);
                if ((f & 8) != 0) { s.secretsTotal++; if ((f & 64) != 0) s.secrets++; }
            }
            // monsters: entities with a monster part; dead when their sprite or the entity says so
            int sa = m.read32(render + 0x34), n = m.read32(game + 400 * 64);
            if (n > 0 && n <= 400 && ok(m, sa)) for (int i = 0; i < n; i++) {
                int e = game + i * 64;
                if (m.read32(e + 56) == 0) continue;
                s.monstersTotal++;
                int info = m.read32(e + 16), si = (info & 0xffff) - 1;
                boolean dead = (info & 0x20000) != 0 || (si >= 0 && (m.read32(sa + si * 36 + 8) & 0x1000000) != 0);
                if (dead) s.monsters++;
            }
            return s;
        } catch (RuntimeException e) { return null; }
    }

    private static boolean ok(Memory m, int a) { return a > 0 && a < m.size; }

    /**
     * Level, XP and the XP the level needs, for the XP bar: {level, xp, nextXp}, or null while the game isn't
     * being played. The game keeps XP per level (it takes the level's XP off when you go up a level), so the
     * bar is simply xp / nextXp.
     */
    public static int[] xp(Brew b) {
        try {
            Memory m = b.mem;
            if (b.app == 0) return null;
            int canvas = m.read32(b.app + APP_CANVAS), p = m.read32(b.app + APP_PLAYER);
            if (!ok(m, canvas) || !ok(m, p)) return null;
            int st = m.read32(canvas + CV_STATE);
            if (st != ST_PLAYING && st != 4) return null;   // playing or in a fight
            int level = m.read32(p + 40), xp = m.read32(p + 44), next = m.read32(p + 48);
            if (level < 1 || level > 99 || next <= 0 || next > 100000 || xp < 0 || xp > next) return null;
            return new int[]{level, xp, next};
        } catch (RuntimeException e) { return null; }
    }

    // ------------------------------------------------------------------ save now
    /** Result of a save: still going, saved, or couldn't. */
    public static final int SAVE_BUSY = 0, SAVE_DONE = 1, SAVE_FAILED = 2;
    private int step = -1, waited, presses;

    public boolean saving() { return step >= 0; }

    /** Starts saving through the game's own menu (Menu, then Save Game). Only while playing. Emulator thread. */
    public boolean startSave(Brew b) {
        if (step >= 0 || state(b) != ST_PLAYING) return false;
        step = 0; waited = 0; presses = 0;
        b.keyDown(Brew.AVK_SOFT1); b.keyUp(Brew.AVK_SOFT1);
        return true;
    }

    /** Moves a save along; call between the game's frames. Returns SAVE_BUSY until it is done. */
    public int tick(Brew b) {
        if (step < 0) return SAVE_FAILED;
        int st = state(b);
        waited++;
        switch (step) {
            case 0:   // waiting for the game's menu
                if (st == ST_MENU) { step = 1; waited = 0; }
                else if (waited > 60) return fail();
                return SAVE_BUSY;
            case 1: {   // move the cursor to "Save Game", then choose it
                String sel = selectedItem(b);
                if (sel == null) return fail();
                if (sel.equals("Save Game")) { b.keyDown(Brew.AVK_SELECT); b.keyUp(Brew.AVK_SELECT); step = 2; waited = 0; return SAVE_BUSY; }
                if (waited < 3) return SAVE_BUSY;   // a few frames between presses
                if (++presses > 12) return fail();
                b.keyDown(Brew.AVK_DOWN); b.keyUp(Brew.AVK_DOWN); waited = 0;
                return SAVE_BUSY;
            }
            default:   // saving: done once the game is back to play
                if (st == ST_PLAYING) { step = -1; return SAVE_DONE; }
                if (waited > 400) return fail();
                return SAVE_BUSY;
        }
    }

    private int fail() { step = -1; return SAVE_FAILED; }

    private static int state(Brew b) {
        try {
            int canvas = b.mem.read32(b.app + APP_CANVAS);
            return ok(b.mem, canvas) ? b.mem.read32(canvas + CV_STATE) : -1;
        } catch (RuntimeException e) { return -1; }
    }

    /** Text of the menu item under the game's cursor, or null. */
    private static String selectedItem(Brew b) {
        try {
            Memory m = b.mem;
            int ms = m.read32(b.app + APP_MENUSYS);
            if (!ok(m, ms)) return null;
            int n = m.read32(ms + MS_NUM), sel = m.read32(ms + MS_SEL);
            if (n <= 0 || n > 80 || sel < 0 || sel >= n) return null;
            StringBuilder s = new StringBuilder();
            int a = ms + MS_ITEMS + sel * ITEM;
            for (int i = 0; i < 18; i++) { int c = m.read8(a + i); if (c == 0) break; s.append((char) c); }
            return s.toString();
        } catch (RuntimeException e) { return null; }
    }
}
