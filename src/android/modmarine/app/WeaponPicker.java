package modmarine.app;

import brewemu.doomrpg.DoomWeapons;

import static modmarine.app.PixelArt.*;

/**
 * The weapon picker: a window over the game with the weapons in a 3 x 3 grid (a fourth row for the dogs once
 * one is owned), the ammo of each, the weapon in hand and a cursor. Pure Java: lays out and paints the window
 * in art pixels; the weapon pictures are drawn on top by GameView (see {@link #picRect}).
 */
final class WeaponPicker {
    static final int CW = 36, CH = 30, GAP = 3, PAD = 5, TOP = 13, FOOT = 21;

    /** What the game says (copied from DoomWeapons on the UI thread). */
    int owned, weapon = -1;
    final int[] ammo = new int[6];
    int cursor = 2;

    int rows() { return (owned & 0xe00) != 0 ? 4 : 3; }
    int width() { return 3 * CW + 2 * GAP + 2 * PAD; }
    int height() { return TOP + rows() * CH + (rows() - 1) * GAP + FOOT; }

    boolean owns(int w) { return (owned & (1 << w)) != 0; }
    boolean usable(int w) { return owns(w) && (ammo[DoomWeapons.AMMO_TYPE[w]] > 0 || DoomWeapons.AMMO_USE[w] == 0); }

    /** Weapon number in cell i (row by row), or -1 for the empty cells of the dog row. */
    int weaponAt(int i) { return i >= 0 && i < 12 ? i : -1; }

    int cells() { return rows() * 3; }

    /** Cell box in window art pixels (the window has a 1-pixel outline): x0, y0 (size CW x CH). */
    int cellX(int i) { return 1 + PAD + (i % 3) * (CW + GAP); }
    int cellY(int i) { return 1 + TOP + (i / 3) * (CH + GAP); }

    /** Where the weapon picture goes inside cell i: x0, y0, x1, y1 (window art pixels, inclusive). */
    int[] picRect(int i) {
        int x = cellX(i), y = cellY(i);
        boolean dog = weaponAt(i) >= 9;
        return new int[]{x + 3, y + 2, x + CW - 4, y + (dog ? CH - 3 : CH - 11)};
    }

    /** Where the ammo icon goes in cell i (left of the number): x1 (right edge), y centre. */
    int[] ammoIconAt(int i) { return new int[]{cellX(i) + CW / 2 - 2, cellY(i) + CH - 6}; }

    void moveCursor(int dx, int dy) {
        int n = cells(), c = cursor;
        int x = c % 3, y = c / 3;
        x = Math.max(0, Math.min(2, x + dx)); y = Math.max(0, Math.min(rows() - 1, y + dy));
        cursor = Math.min(n - 1, y * 3 + x);
    }

    /** Cell under the window art pixel (x, y), or -1. */
    int cellAt(float x, float y) {
        for (int i = 0; i < cells(); i++) {
            int cx = cellX(i), cy = cellY(i);
            if (x >= cx - 1 && x < cx + CW + 1 && y >= cy - 1 && y < cy + CH + 1) return i;
        }
        return -1;
    }

    /** Paints the window (frames, numbers, names); weapon pictures and ammo icons are added by GameView. */
    PixelArt paint(boolean controller) {
        int W = width(), H = height();
        PixelArt a = new PixelArt(W + 2, H + 2);
        a.bevelBox(1, 1, W, H, D3, D5, D1, false);
        a.text("WEAPONS", PAD + 1, 4, OR, 1);
        for (int i = 0; i < cells(); i++) {
            int w = weaponAt(i), x = cellX(i), y = cellY(i);
            if (i == cursor) a.bevelBox(x - 1, y - 1, x + CW, y + CH, R2, OR2, R1, false);
            a.well(x, y, x + CW - 1, y + CH - 1);
            if (w < 0 || !owns(w)) continue;
            if (w < 9 && DoomWeapons.AMMO_USE[w] > 0) {
                String t = String.valueOf(ammo[DoomWeapons.AMMO_TYPE[w]]);
                a.text(t, x + CW / 2 + 1, y + CH - 8, usable(w) ? BONE : R3, 1);
            }
            if (w == weapon) a.rect(x + 2, y + 2, x + 4, y + 4, OR);
        }
        int w = weaponAt(cursor);
        String name = w < 0 || !owns(w) ? "" : DoomWeapons.NAMES[w];
        if (w >= 0 && owns(w) && w < 9 && DoomWeapons.AMMO_USE[w] > 0)
            name += "  " + ammo[DoomWeapons.AMMO_TYPE[w]] + " " + DoomWeapons.AMMO_NAMES[DoomWeapons.AMMO_TYPE[w]];
        if (w >= 0 && owns(w) && !usable(w)) name += "  EMPTY";
        int fy = TOP + rows() * CH + (rows() - 1) * GAP + 1;
        a.text(name, 1 + W / 2 - PixelArt.textWidth(name, 1) / 2, fy + 3, OR2, 1);
        String hint = controller ? "DPAD + A    B CLOSE" : "TAP A WEAPON";
        a.text(hint, 1 + W / 2 - PixelArt.textWidth(hint, 1) / 2, fy + 12, BONE2, 1);
        return a;
    }
}
