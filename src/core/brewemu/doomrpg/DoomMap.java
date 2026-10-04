package brewemu.doomrpg;

import brewemu.brew.Brew;
import brewemu.cpu.Memory;

/**
 * Mini-map for Doom RPG (BREW): reads the game's own automap data from the emulated phone's memory and draws a
 * small map around the player. It only reads; the game is never changed. The layout of the game's data is the
 * one documented by the DoomRPG-RE project (render: lines, map sprites and the 32 x 32 automap flags; canvas:
 * view position and angle, game state). Anything that does not look right makes {@link #read} return false, so
 * the mini-map simply isn't shown.
 */
public final class DoomMap {
    // the applet's pointers (as also used for the Vibrate setting)
    private static final int APP_CANVAS = 0x104, APP_RENDER = 0x108, APP_GAME = 0x110;
    // game: 400 entities of 64 bytes, their count, then the entity list of each of the 1024 tiles
    private static final int GM_ENTITY_DB = 400 * 64 + 4, ENT_DEF = 0, ENT_NEXT = 4, ENT_INFO = 16, DEF_TYPE = 2;
    // canvas fields
    private static final int CV_VIEW_X = 0x58, CV_VIEW_Y = 0x5c, CV_VIEW_ANGLE = 0x64, CV_STATE = 0xa4;
    private static final int ST_PLAYING = 3, ST_COMBAT = 4;
    // render fields
    private static final int RD_SCREEN_W = 0x08, RD_LINES = 0x2c, RD_LINES_LEN = 0x30, RD_SPRITES = 0x34,
            RD_NUM_SPRITES = 0x3c, RD_MAP_FLAGS = 0x54;
    private static final int LINE_SIZE = 32, LINE_FLAGS = 28, SPRITE_SIZE = 36, SPRITE_INFO = 8;
    // automap flags and bits (as the game's own automap uses them)
    private static final int AM_WALL = 1, AM_ENTRANCE = 4, AM_VISITED = 16;
    private static final int LINE_SEEN = 128, LINE_DOOR = 4;
    private static final int SPR_SEEN = 0x10000000, SPR_OPEN = 0x40000,
            SPR_HORIZ_A = 0x80000, SPR_HORIZ_B = 0x100000;
    private static final int ET_MONSTER = 2, ET_DOOR_A = 14, ET_DOOR_B = 15;

    // the game's automap colours
    public static final int C_FLOOR = 0xff660000, C_ENTRANCE = 0xffffaa00, C_WALL = 0xffcc0000, C_DOOR_LINE = 0xffcc9900,
            C_DOOR = 0xffcc0000, C_DOOR_SHUT = 0xff880000, C_MONSTER = 0xff33bb00;

    // what was read
    public final byte[] flags = new byte[1024];
    private int[] lines = new int[0];       // x1, y1, x2, y2, colour per seen line
    private int lineCount;
    private int[] marks = new int[0];       // kind, x, y per door / monster (map units)
    private int markCount;
    /** Player position in map units (64 per tile) and angle (256 per turn; 0 = east, 64 = north). */
    public int viewX, viewY, angle;

    /** Reads the game's map; false if the game isn't being played right now or the data doesn't look right. */
    public boolean read(Brew b) {
        try { return readUnsafe(b); } catch (RuntimeException e) { return false; }
    }

    private boolean readUnsafe(Brew b) {
        Memory m = b.mem;
        if (b.app == 0) return false;
        int canvas = m.read32(b.app + APP_CANVAS), render = m.read32(b.app + APP_RENDER);
        if (!ptr(m, canvas) || !ptr(m, render)) return false;
        int state = m.read32(canvas + CV_STATE);
        if (state != ST_PLAYING && state != ST_COMBAT) return false;
        int sw = m.read32(render + RD_SCREEN_W);
        if (sw != 176 && sw != 240 && sw != 128) return false;
        viewX = m.read32(canvas + CV_VIEW_X); viewY = m.read32(canvas + CV_VIEW_Y);
        angle = m.read32(canvas + CV_VIEW_ANGLE) & 255;
        if (viewX < 0 || viewX >= 32 * 64 || viewY < 0 || viewY >= 32 * 64) return false;
        m.read(render + RD_MAP_FLAGS, flags, 0, 1024);
        for (int i = 0; i < 1024; i++) if ((flags[i] & 0xe0) != 0) return false;

        int la = m.read32(render + RD_LINES), ln = m.read32(render + RD_LINES_LEN);
        if (ln < 0 || ln > 4096 || (ln > 0 && (!ptr(m, la) || !ptr(m, la + ln * LINE_SIZE - 1)))) return false;
        if (lines.length < ln * 5) lines = new int[ln * 5];
        lineCount = 0;
        for (int i = 0; i < ln; i++) {
            int a = la + i * LINE_SIZE, f = m.read32(a + LINE_FLAGS);
            if ((f & LINE_SEEN) == 0) continue;
            int k = lineCount++ * 5;
            lines[k] = m.read32(a); lines[k + 1] = m.read32(a + 4);
            lines[k + 2] = m.read32(a + 12); lines[k + 3] = m.read32(a + 16);
            lines[k + 4] = (f & LINE_DOOR) != 0 ? C_DOOR_LINE : C_WALL;
        }

        // doors and monsters: the entities standing on each tile (as the game's automap finds them)
        int game = m.read32(b.app + APP_GAME);
        if (!ptr(m, game)) return false;
        int db = game + GM_ENTITY_DB;
        int sa = m.read32(render + RD_SPRITES), sn = m.read32(render + RD_NUM_SPRITES);
        if (sn < 0 || sn > 4096 || (sn > 0 && (!ptr(m, sa) || !ptr(m, sa + sn * SPRITE_SIZE - 1)))) return false;
        markCount = 0;
        for (int t = 0; t < 1024; t++) {
            int ent = m.read32(db + 4 * t);
            for (int guard = 0; ent != 0 && guard < 64; guard++, ent = m.read32(ent + ENT_NEXT)) {
                if (!ptr(m, ent)) return false;
                int def = m.read32(ent + ENT_DEF);
                if (!ptr(m, def)) return false;
                int type = m.read8(def + DEF_TYPE), kind, x, y;
                if (type == ET_MONSTER) {
                    int fl = flags[t];
                    if ((fl & AM_VISITED) == 0 || (fl & AM_WALL) != 0) continue;
                    kind = 8; x = (t & 31) * 64; y = (t >> 5) * 64;
                } else if (type == ET_DOOR_A || type == ET_DOOR_B) {
                    int si = (m.read32(ent + ENT_INFO) & 0xffff) - 1;
                    if (si < 0 || si >= sn) continue;
                    int a = sa + si * SPRITE_SIZE, info = m.read32(a + SPRITE_INFO);
                    if ((info & SPR_SEEN) == 0) continue;
                    kind = ((info & (SPR_HORIZ_A | SPR_HORIZ_B)) != 0 ? 1 : 2) | ((info & SPR_OPEN) != 0 ? 4 : 0);
                    x = m.read32(a); y = m.read32(a + 4);
                } else continue;
                if (marks.length < (markCount + 1) * 3) marks = java.util.Arrays.copyOf(marks, marks.length * 2 + 48);
                int k = markCount++ * 3;
                marks[k] = kind; marks[k + 1] = x; marks[k + 2] = y;
            }
        }
        return true;
    }

    private static boolean ptr(Memory m, int a) { return a > 0 && a < m.size; }

    /** The game's state number while it asks for a door code. */
    public static final int ST_PASSWORD = 8;

    /** The game's current state (0 legal screen, 2 menu, 3 playing, 5 its map, 8 door code...), or -1 if unknown. */
    public static int state(Brew b) {
        try {
            if (b.app == 0) return -1;
            int canvas = b.mem.read32(b.app + APP_CANVAS);
            if (!ptr(b.mem, canvas)) return -1;
            int s = b.mem.read32(canvas + CV_STATE);
            return s >= 0 && s < 32 ? s : -1;
        } catch (RuntimeException e) { return -1; }
    }

    // ------------------------------------------------------------------ drawing
    /**
     * Draws the map around the player: tiles x tiles, px pixels per tile, north up, the player in the middle.
     * Pixels are ARGB; the background is see-through dark.
     */
    public void drawAround(int[] out, int size, int px, int background) {
        java.util.Arrays.fill(out, 0, size * size, background);
        // the player's position at the middle of the picture; map units -> pixels: (u - origin) * px / 64
        int ox = viewX * px - size * 32, oy = viewY * px - size * 32;   // origin in units * px (scaled by 64)
        draw(out, size, size, px, ox, oy);
        player(out, size, size, size / 2, size / 2, px);
        frame(out, size, size);
    }

    /** The whole 32 x 32 map like the game's own automap screen (used to check the reading against the game). */
    public void drawFull(int[] out, int w, int h, int cell, int left, int top) {
        java.util.Arrays.fill(out, 0, w * h, 0xff000000);
        draw(out, w, h, cell, -left * 64, -top * 64);
    }

    /** ox, oy: picture origin in map units times px (so a map unit u lands at (u * px - ox) / 64). */
    private void draw(int[] out, int w, int h, int px, int ox, int oy) {
        for (int ty = 0; ty < 32; ty++) for (int tx = 0; tx < 32; tx++) {
            int f = flags[ty * 32 + tx];
            if ((f & AM_VISITED) == 0 || (f & AM_WALL) != 0) continue;
            int x0 = (tx * 64 * px - ox) >> 6, y0 = (ty * 64 * px - oy) >> 6;
            rect(out, w, h, x0, y0, px, px, (f & AM_ENTRANCE) != 0 ? C_ENTRANCE : C_FLOOR);
        }
        for (int i = 0; i < markCount; i++) {
            int k = i * 3;
            if (marks[k] != 8) continue;
            int x0 = (marks[k + 1] * px - ox) >> 6, y0 = (marks[k + 2] * px - oy) >> 6;
            rect(out, w, h, x0 + px / 2, y0 + px / 2, px / 2, px / 2, C_MONSTER);
        }
        for (int i = 0; i < markCount; i++) {
            int k = i * 3, kind = marks[k];
            if (kind == 8) continue;
            int x = marks[k + 1], y = marks[k + 2];
            int x1 = x, y1 = y, x2 = x, y2 = y;
            if ((kind & 1) != 0) { x1 = x - 32; x2 = x + 32; } else { y1 = y - 32; y2 = y + 32; }
            line(out, w, h, (x1 * px - ox + 32) >> 6, (y1 * px - oy + 32) >> 6, (x2 * px - ox + 32) >> 6, (y2 * px - oy + 32) >> 6,
                    (kind & 4) != 0 ? C_DOOR : C_DOOR_SHUT);
        }
        for (int i = 0; i < lineCount; i++) {
            int k = i * 5;
            line(out, w, h, (lines[k] * px - ox + 32) >> 6, (lines[k + 1] * px - oy + 32) >> 6,
                    (lines[k + 2] * px - ox + 32) >> 6, (lines[k + 3] * px - oy + 32) >> 6, lines[k + 4]);
        }
    }

    /** A small arrow for the player, pointing the way they face (nearest of the four directions). */
    private void player(int[] out, int w, int h, int cx, int cy, int px) {
        int dir = ((angle + 32) & 255) >> 6;   // 0 east, 1 north, 2 west, 3 south
        int dx = dir == 0 ? 1 : dir == 2 ? -1 : 0, dy = dir == 1 ? -1 : dir == 3 ? 1 : 0;
        int len = Math.max(2, px / 2 + 1);
        int ink = 0xffffffff, edge = 0xff000000;
        // a filled triangle: rows across the facing direction get narrower towards the tip
        for (int pass = 0; pass < 2; pass++) {
            int grow = pass == 0 ? 1 : 0, c = pass == 0 ? edge : ink;
            for (int s = -len - grow; s <= len - 1 + grow; s++) {
                int half = (len - s) / 2 + grow;   // wide at the back, a point at the front
                if (half < 0) continue;
                for (int t = -half; t <= half; t++) {
                    int x = cx + dx * s - dy * t, y = cy + dy * s + dx * t;
                    if (x >= 0 && x < w && y >= 0 && y < h) out[y * w + x] = c;
                }
            }
        }
    }

    private static void frame(int[] out, int w, int h) {
        int c = 0xff3a1010;
        for (int x = 0; x < w; x++) { out[x] = c; out[(h - 1) * w + x] = c; }
        for (int y = 0; y < h; y++) { out[y * w] = c; out[y * w + w - 1] = c; }
    }

    private static void rect(int[] out, int w, int h, int x0, int y0, int rw, int rh, int c) {
        int xa = Math.max(0, x0), ya = Math.max(0, y0), xb = Math.min(w, x0 + rw), yb = Math.min(h, y0 + rh);
        for (int y = ya; y < yb; y++) for (int x = xa; x < xb; x++) out[y * w + x] = c;
    }

    private static void line(int[] out, int w, int h, int x0, int y0, int x1, int y1, int c) {
        if ((x0 < 0 && x1 < 0) || (y0 < 0 && y1 < 0) || (x0 >= w && x1 >= w) || (y0 >= h && y1 >= h)) return;
        int dx = Math.abs(x1 - x0), dy = -Math.abs(y1 - y0), sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1, err = dx + dy;
        while (true) {
            if (x0 >= 0 && x0 < w && y0 >= 0 && y0 < h) out[y0 * w + x0] = c;
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 >= dy) { err += dy; x0 += sx; }
            if (e2 <= dx) { err += dx; y0 += sy; }
        }
    }
}
