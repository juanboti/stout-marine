package modmarine.app;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import static modmarine.app.PixelArt.*;

/** Pixel-art control deck: layout, hit areas and rendering, in art pixels. Pure Java (testable off-device). */
final class Deck {
    static final int K_UP = GameView.K_UP, K_DOWN = GameView.K_DOWN, K_LEFT = GameView.K_LEFT, K_RIGHT = GameView.K_RIGHT,
            K_FIRE = GameView.K_FIRE, K_MENU = GameView.K_MENU, K_MAP = GameView.K_MAP, K_STRAFE_L = GameView.K_STRAFE_L,
            K_STRAFE_R = GameView.K_STRAFE_R, K_WPN_PREV = GameView.K_WPN_PREV, K_WPN_NEXT = GameView.K_WPN_NEXT,
            K_WAIT = GameView.K_WAIT, K_SETTINGS = GameView.K_SETTINGS, K_WEAPONS = GameView.K_WEAPONS;
    static final int CELL = 0, DISC = 1, FIRE = 2, SLOT = 3, KEY = 4, SHOULDER = 5;
    /** Keypad's own "close" key: hides the number keys again. */
    static final int K_CLOSE = -100;

    final float dp;
    float artPx = 1;
    int artW, artH;
    PixelArt art;
    /** Game picture position in screen pixels: left, top, right, bottom. */
    final float[] game = new float[4];
    final ArrayList<int[]> panels = new ArrayList<int[]>();
    final ArrayList<Btn> buttons = new ArrayList<Btn>();
    final ArrayList<Btn> keypad = new ArrayList<Btn>();
    int[] clusterWell;
    /** The cross: centre cell (not a button) and arm width, in art pixels. */
    int crossCx, crossCy, crossCell;
    int slotCx, slotCy;
    String label; int labelX, labelY;
    int kpX0, kpY0, kpX1, kpY1;
    boolean keypadOn;
    /** Flips while the keypad is open so its toggle button pulses. */
    boolean pulse;

    /** Touch deck around the game (phones), or handheld (game in the middle, physical buttons). */
    static final int MODE_TOUCH = 0, MODE_HANDHELD = 1;
    int mode = MODE_TOUCH;
    /** True when the last layout really used the handheld arrangement. */
    boolean handheldLayout;
    /** Handheld mode with the number keys open: game on the left, a big keypad on the right, a d-pad cursor. */
    boolean bigKeypad;
    /** Keypad key under the controller cursor (index into keypad; 12 = CLOSE). */
    int cursor = 4;
    /** Short button guide under the game while the big keypad is open: pairs of button, action. */
    static String[] hint() { return new String[]{"DPAD", "CHOOSE A KEY", PadBinds.nameOf(PadBinds.FIRE), "PRESS IT", PadBinds.nameOf(PadBinds.MENU), "CLOSE"}; }
    int hintX, hintY;
    /** Button guide (handheld mode): drawn inside legendX..+legendW, legendY..+legendH. */
    String[][] legend;
    int legendX, legendY, legendW, legendH;

    /** XP bar (Diablo style): shown when xpOn; level, XP in this level and the XP the level needs (level 0 = not known yet). */
    boolean xpOn = true;
    int xpLevel, xp, xpNext;
    /** Where it goes (art pixels): bar x0..x1 from y0 (groove xpInner high), level socket, the "to next level" text. */
    int xpX0 = -1, xpX1, xpY0, xpInner, xpSockX, xpSockY, xpSockR, xpScale = 1, xpTextX, xpTextY, xpTextLines;
    boolean xpTextRight, xpInBar;
    /** Handheld XP block: 0 none, 1 tall (LEVEL, socket, bar, three lines), 2 short (socket + bar, one line). */
    int xpHand;

    /** The weapon in hand and what the arrows would switch to (weapon numbers; -1 = not known, e.g. in menus). */
    int wpnCur = -1, wpnPrev = -1, wpnNext = -1;

    /** Size of the game picture in game pixels (sets its aspect ratio). */
    final int gW, gH;

    Deck(float dp, int gameW, int gameH) { this.dp = dp; gW = gameW; gH = gameH; }

    static final class Btn {
        int kind, key;
        int x0, y0, x1, y1;   // CELL / SLOT half / KEY
        int cx, cy, r;        // DISC / FIRE
        String[] icon; String text;
        boolean repeat, longPress, longFired;
        int pointer = -1; long downAt, lastRepeat;
        boolean hit(float ax, float ay, float slop) {
            if (kind == DISC || kind == FIRE) {
                float dx = ax - cx, dy = ay - cy, rr = r + 1 + slop;
                return dx * dx + dy * dy <= rr * rr;
            }
            return ax >= x0 - slop && ax <= x1 + 1 + slop && ay >= y0 - slop && ay <= y1 + 1 + slop;
        }
        int centerX() { return (kind == DISC || kind == FIRE) ? cx : (x0 + x1) / 2; }
        int centerY() { return (kind == DISC || kind == FIRE) ? cy : (y0 + y1) / 2; }
    }

    private Btn disc(int key, int cx, int cy, int r, String[] icon) {
        Btn b = new Btn(); b.kind = DISC; b.key = key; b.cx = cx; b.cy = cy; b.r = r; b.icon = icon; buttons.add(b); return b;
    }

    /** A d-pad like a handheld's: up / down walk, left / right turn. cell = arm width in art pixels. */
    private void cross(int cx, int cy, int cell) {
        final int gap = 1;
        crossCx = cx; crossCy = cy; crossCell = cell;
        int span = cell * 3 + gap * 2, x0 = cx - span / 2, y0 = cy - span / 2;
        clusterWell = new int[]{x0 - 3, y0 - 3, x0 + span + 2, y0 + span + 2};
        int[][] pos = {{1, 0}, {0, 1}, {2, 1}, {1, 2}};
        int[] keys = {K_UP, K_LEFT, K_RIGHT, K_DOWN};
        String[][] icons = {Icons.UP, Icons.TURN_L, Icons.TURN_R, Icons.DOWN};
        for (int i = 0; i < 4; i++) {
            Btn b = new Btn(); b.kind = CELL; b.key = keys[i]; b.icon = icons[i]; b.repeat = true;
            b.x0 = x0 + pos[i][0] * (cell + gap); b.y0 = y0 + pos[i][1] * (cell + gap); b.x1 = b.x0 + cell - 1; b.y1 = b.y0 + cell - 1;
            buttons.add(b);
        }
    }

    /** Shoulder-style side-step button, like L / R on a handheld. */
    private void shoulder(int key, int x0, int y0, int x1, int y1) {
        Btn b = new Btn(); b.kind = SHOULDER; b.key = key; b.repeat = true;
        b.text = key == K_STRAFE_L ? "L" : "R"; b.icon = key == K_STRAFE_L ? Icons.STEP_L : Icons.STEP_R;
        b.x0 = x0; b.y0 = y0; b.x1 = x1; b.y1 = y1;
        buttons.add(b);
    }

    /** Weapon slot: arrows (with the weapon they switch to) at the ends, the weapon in hand in the middle (opens the picker). */
    private void slot(int cx, int cy) {
        slotCx = cx; slotCy = cy;
        Btn l = new Btn(); l.kind = SLOT; l.key = K_WPN_PREV; l.x0 = cx - 28; l.x1 = cx - 12; l.y0 = cy - 7; l.y1 = cy + 7;
        Btn m = new Btn(); m.kind = SLOT; m.key = K_WEAPONS; m.x0 = cx - 11; m.x1 = cx + 11; m.y0 = cy - 7; m.y1 = cy + 7;
        Btn r = new Btn(); r.kind = SLOT; r.key = K_WPN_NEXT; r.x0 = cx + 12; r.x1 = cx + 28; r.y0 = cy - 7; r.y1 = cy + 7;
        buttons.add(l); buttons.add(m); buttons.add(r);
    }

    private Btn fire(int cx, int cy) {
        Btn b = new Btn(); b.kind = FIRE; b.key = K_FIRE; b.cx = cx; b.cy = cy; b.r = 17; buttons.add(b); return b;
    }

    /**
     * Lays the deck out for a w x h screen: the default places, then the player's own changes (touch layouts).
     * A change that no longer fits (another screen, the XP bar turned on) is left out, so the default is used.
     */
    void layout(int w, int h) {
        disabled.clear();
        for (int i = 0; i < 16; i++) {
            layoutOnce(w, h);
            String bad = null;
            for (Group g : groups) if (g.bad && g.edited && !g.id.equals(dragging)) { bad = g.id; break; }
            if (bad == null) return;
            disabled.add(bad);
        }
    }

    private void layoutOnce(int w, int h) {
        groups.clear(); editable = false;
        buttons.clear(); keypad.clear(); panels.clear(); label = null; legend = null; handheldLayout = false; bigKeypad = false;
        xpX0 = -1; xpHand = 0; xpInBar = false; xpTextRight = false; xpTextLines = 0; xpScale = 1; xpSockR = 0;
        if (mode == MODE_HANDHELD && w >= h * 0.9f) {
            if (keypadOn && layoutBigKeypad(w, h)) return;
            if (layoutHandheld(w, h)) return;
        }
        if (h >= w) {
            // ---------- portrait: game on top, deck below (design grid 135 wide)
            artPx = Math.max(1f, Math.min(w / 135f, 3.2f * dp));
            float s = Math.min((float) w / gW, h * 0.6f / gH);
            float gw = gW * s, gh = gH * s;
            // short screens (square ones): the deck needs about 82 art pixels of height below the game
            artPx = Math.max(1f, Math.min(artPx, (h - gh) / (xpOn ? 96f : 82f)));   // + the XP bar
            setGame((w - gw) / 2, 0, (w + gw) / 2, gh);
            artW = (int) Math.ceil(w / artPx); artH = (int) Math.ceil(h / artPx);
            int top = (int) Math.ceil(gh / artPx), deckH = artH - top;
            int ox = Math.max(0, (artW - 135) / 2);
            panels.add(new int[]{0, top, artW - 1, artH - 1});
            boolean compact = deckH < 110;
            int ty = top + (compact ? 9 : 13);
            if (xpOn) {
                // XP bar right under the game picture (below the deck's top edge), level socket on its left
                xpY0 = top + 5; xpInner = 6; xpX0 = ox + 12; xpX1 = ox + 133;
                xpSockX = ox + 6; xpSockY = xpY0 + 5; xpSockR = 6; xpScale = 1; xpInBar = true; xpTextLines = 0;
                ty = Math.max(ty + 10, top + 23);   // the top row's discs clear the bar
            }
            disc(K_MENU, ox + 13, ty, 7, Icons.MENU).longPress = true;
            disc(K_SETTINGS, ox + 31, ty, 6, Icons.GEAR);
            slot(ox + 67, ty);
            disc(0, ox + 104, ty, 7, Icons.KEYS);
            disc(K_MAP, ox + 122, ty, 7, Icons.MAP);
            // shoulder row: L  wait  R
            int sy0 = ty + (compact ? 11 : 11), sy1 = sy0 + (compact ? 10 : 13);
            shoulder(K_STRAFE_L, ox + 4, sy0, ox + 42, sy1);
            shoulder(K_STRAFE_R, ox + 92, sy0, ox + 130, sy1);
            disc(K_WAIT, ox + 67, (sy0 + sy1) / 2, compact ? 7 : 8, Icons.HOUR).repeat = false;
            // cross on the left, fire on the right
            int cTop = sy1 + 6, avail = artH - 6 - cTop;
            int cell = Math.max(12, Math.min(20, (avail - 2) / 3));
            int cy = cTop + avail / 2;
            cross(ox + 37, cy, cell);
            fire(ox + 106, cy);
            if (artH - 12 > cy + (cell * 3 + 2) / 2 + 3) { label = "STOUT MARINE"; labelX = ox + 130 - PixelArt.textWidth(label, 1); labelY = artH - 9; }
            kpX0 = ox + 4; kpY0 = top + 25 + (ty - top - (compact ? 9 : 13)); kpX1 = ox + 130; kpY1 = artH - 5;
        } else {
            // ---------- landscape: game centred, a deck on each side (~100 art px wide)
            float gh = h, gw = h * (float) gW / gH, side = (w - gw) / 2;
            artPx = Math.min(Math.min(h / 135f, side / 100f), 3.2f * dp);
            if (artPx < 2f * dp) {
                artPx = 2f * dp;
                side = 100 * artPx; gw = Math.max(120, w - 2 * side); gh = Math.min(h, gw * gH / (float) gW); gw = gh * gW / (float) gH;
            }
            setGame((w - gw) / 2, (h - gh) / 2, (w + gw) / 2, (h + gh) / 2);
            artW = (int) Math.ceil(w / artPx); artH = (int) Math.ceil(h / artPx);
            int gl = (int) Math.floor(game[0] / artPx), gr = (int) Math.ceil(game[2] / artPx);
            panels.add(new int[]{0, 0, gl - 1, artH - 1});
            panels.add(new int[]{gr, 0, artW - 1, artH - 1});
            int lx = gl / 2, rx = gr + (artW - gr) / 2, H = artH, W = artW;
            disc(K_MENU, 14, 15, 7, Icons.MENU).longPress = true;
            disc(0, 32, 15, 7, Icons.KEYS);
            disc(K_SETTINGS, 50, 15, 6, Icons.GEAR);
            disc(K_MAP, W - 14, 15, 7, Icons.MAP);
            int lw = gl, rw = W - gr;
            slot(gr + Math.max(30, (rw - 24) / 2), 15);
            shoulder(K_STRAFE_L, 6, 27, Math.min(lw - 7, 46), 40);
            shoulder(K_STRAFE_R, Math.max(gr + 6, W - 47), 27, W - 7, 40);
            int lcell = Math.max(12, Math.min(20, (H - 46 - 6 - 2) / 3));
            cross(lx, H - 6 - (lcell * 3 + 2) / 2 - 2, lcell);
            fire(rx + 14, H - 32);
            disc(K_WAIT, rx - 14, H - 56, 8, Icons.HOUR);
            kpX0 = gr + 4; kpY0 = 44; kpX1 = W - 5; kpY1 = H - 5;
            if (xpOn && rw >= 50 && H - 56 - 8 >= 66) {
                // XP bar in the right panel, between R and the wait button
                xpY0 = 47; xpInner = 6; xpSockR = 7; xpSockX = gr + 4 + xpSockR; xpSockY = xpY0 + 5;
                xpX0 = xpSockX + xpSockR + 2; xpX1 = W - 5; xpScale = 1; xpInBar = true;
                xpTextLines = rw >= 70 ? 1 : 0; xpTextRight = true; xpTextX = W - 5; xpTextY = 60;
            }
        }
        applyEdits(h >= w);
        if (label != null && !edits(h >= w).isEmpty()) label = null;
        makeKeypad(true);
        if (!probe || art == null || art.w != artW || art.h != artH) art = new PixelArt(artW, artH);
    }

    // ---------------------------------------------------------------- the player's own touch layout
    /** A group of buttons that moves together (and, for the cross and fire button, can change size). */
    static final class Group {
        String id; int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE;
        boolean sizable, edited, bad;
        void add(int a0, int b0, int a1, int b1) { x0 = Math.min(x0, a0); y0 = Math.min(y0, b0); x1 = Math.max(x1, a1); y1 = Math.max(y1, b1); }
        boolean overlaps(int a0, int b0, int a1, int b1) { return a0 <= x1 && a1 >= x0 && b0 <= y1 && b1 >= y0; }
        int cx() { return (x0 + x1) / 2; }
    }
    static final String[] GROUP_NAMES_IDS = {"top", "topl", "topr", "steps", "stepl", "stepr", "wait", "cross", "fire"};
    static final String[] GROUP_NAMES = {"TOP BUTTONS", "TOP BUTTONS", "WEAPONS AND MAP", "STEP BUTTONS", "L", "R", "WAIT", "CROSS", "FIRE"};
    static String groupName(String id) { for (int i = 0; i < GROUP_NAMES_IDS.length; i++) if (GROUP_NAMES_IDS[i].equals(id)) return GROUP_NAMES[i]; return id.toUpperCase(); }

    /** Changes per layout (portrait, landscape): group id -> {dx, dy, size %}. */
    final HashMap<String, int[]> editsP = new HashMap<String, int[]>(), editsL = new HashMap<String, int[]>();
    HashMap<String, int[]> edits(boolean portrait) { return portrait ? editsP : editsL; }
    final ArrayList<Group> groups = new ArrayList<Group>();
    /** Changes left out because they don't fit. */
    final HashSet<String> disabled = new HashSet<String>();
    /** True for the touch layouts (the ones that can be changed). */
    boolean editable;
    /** Edit mode: the dotted grid and outlines; the group selected / being dragged. */
    boolean editing;
    String selected, dragging;
    /** Where groups may go (art pixels, x0 y0 x1 y1 each), and what they must stay clear of. */
    final ArrayList<int[]> regions = new ArrayList<int[]>(), obstacles = new ArrayList<int[]>();

    private String groupOf(Btn b, boolean portrait) {
        switch (b.kind) {
            case CELL: return "cross";
            case FIRE: return "fire";
            case SHOULDER: return portrait ? "steps" : (b.key == K_STRAFE_L ? "stepl" : "stepr");
            case SLOT: return portrait ? "top" : "topr";
            case DISC:
                if (b.key == K_WAIT) return portrait ? "steps" : "wait";
                return portrait ? "top" : (b.key == K_MAP ? "topr" : "topl");   // by button, not by place (they can move)
        }
        return "other";
    }

    static final int SIZE_MIN = 80, SIZE_MAX = 140;

    /** Saved form of a layout's changes: "id:dx:dy:size;..." */
    static String saveEdits(HashMap<String, int[]> ed) {
        StringBuilder s = new StringBuilder();
        for (String id : ed.keySet()) { int[] e = ed.get(id); if (s.length() > 0) s.append(';'); s.append(id).append(':').append(e[0]).append(':').append(e[1]).append(':').append(e[2]); }
        return s.toString();
    }

    static void loadEdits(String s, HashMap<String, int[]> ed) {
        ed.clear();
        if (s == null || s.isEmpty()) return;
        for (String part : s.split(";")) {
            String[] f = part.split(":");
            if (f.length != 4) continue;
            try { ed.put(f[0], new int[]{Integer.parseInt(f[1]), Integer.parseInt(f[2]), Integer.parseInt(f[3])}); } catch (NumberFormatException e) { /* skip */ }
        }
    }

    /** The touch layout in use is the portrait one. */
    boolean portraitLayout;

    private void applyEdits(boolean portrait) {
        editable = true; portraitLayout = portrait;
        HashMap<String, int[]> ed = edits(portrait);
        // where groups may go: the panels (below the XP bar), and always where they are by default
        regions.clear(); obstacles.clear();
        int xpBottom = xpX0 >= 0 ? xpY0 + xpInner + 3 + (xpTextLines > 0 && xpTextRight ? 12 : 0) : -1;
        if (portrait) {
            int[] p = panels.get(0);
            regions.add(new int[]{p[0] + 1, Math.max(p[1] + 6, xpBottom + 2), p[2] - 1, p[3] - 1});
        } else {
            for (int[] p : panels) regions.add(new int[]{p[0] + 1, p[1] + 1, p[2] - 1, p[3] - 1});
            if (xpX0 >= 0) obstacles.add(new int[]{Math.min(xpX0, xpSockX - xpSockR) - 1, xpY0 - 1, xpX1 + 1, xpBottom + 1});
        }
        buildGroups(portrait, ed);
        for (Group g : groups) {
            int cx = g.cx(), cy = (g.y0 + g.y1) / 2;
            for (int[] r : regions)
                if (cx >= r[0] && cx <= r[2] && cy >= r[1] && cy <= r[3]) { r[0] = Math.min(r[0], g.x0); r[1] = Math.min(r[1], g.y0); r[2] = Math.max(r[2], g.x1); r[3] = Math.max(r[3], g.y1); }
        }
        // move / resize
        int cellsAt = -1;
        for (String id : ed.keySet()) {
            if (disabled.contains(id)) continue;
            int[] e = ed.get(id);
            int dx = e[0], dy = e[1], size = Math.max(SIZE_MIN, Math.min(SIZE_MAX, e[2]));
            if (id.equals("cross")) {
                int cell = Math.max(10, Math.min(28, Math.round(crossCell * size / 100f))), cx = crossCx + dx, cy = crossCy + dy;
                for (int i = buttons.size() - 1; i >= 0; i--) if (buttons.get(i).kind == CELL) buttons.remove(i);
                cross(cx, cy, cell);
                continue;
            }
            boolean slot = false;
            for (Btn b : buttons) {
                if (!id.equals(groupOf(b, portrait))) continue;
                if (b.kind == DISC || b.kind == FIRE) {
                    b.cx += dx; b.cy += dy;
                    if (b.kind == FIRE) b.r = Math.max(10, Math.min(26, Math.round(17 * size / 100f)));
                } else { b.x0 += dx; b.x1 += dx; b.y0 += dy; b.y1 += dy; slot |= b.kind == SLOT; }
            }
            if (slot) { slotCx += dx; slotCy += dy; }
        }
        buildGroups(portrait, ed);
        for (Group g : groups) g.bad = !fits(g);
    }

    /** Groups and their boxes (what is drawn, shadows included). */
    private void buildGroups(boolean portrait, HashMap<String, int[]> ed) {
        groups.clear();
        boolean wellDone = false;
        for (Btn b : buttons) {
            String id = groupOf(b, portrait);
            Group g = null;
            for (Group q : groups) if (q.id.equals(id)) g = q;
            if (g == null) { g = new Group(); g.id = id; g.sizable = id.equals("cross") || id.equals("fire"); g.edited = ed.containsKey(id) && !disabled.contains(id); groups.add(g); }
            switch (b.kind) {
                case CELL: if (!wellDone) { g.add(clusterWell[0] + 2, clusterWell[1] + 2, clusterWell[2] - 2, clusterWell[3] - 2); wellDone = true; } break;
                case DISC: case FIRE: g.add(b.cx - b.r, b.cy - b.r, b.cx + b.r, b.cy + b.r + 1); break;
                case SLOT: g.add(slotCx - 28, slotCy - 7, slotCx + 28, slotCy + 7); break;
                default: g.add(b.x0, b.y0, b.x1, b.y1);
            }
        }
    }

    /** Inside one region, clear of the obstacles and of the other groups. */
    boolean fits(Group g) {
        boolean in = false;
        for (int[] r : regions) if (g.x0 >= r[0] && g.y0 >= r[1] && g.x1 <= r[2] && g.y1 <= r[3]) in = true;
        if (!in) return false;
        for (int[] o : obstacles) if (g.overlaps(o[0], o[1], o[2], o[3])) return false;
        for (Group q : groups) if (q != g && !ignore.contains(q.id) && g.overlaps(q.x0, q.y0, q.x1, q.y1)) return false;
        return true;
    }

    /** Groups not checked against (while placing groups one by one). */
    final HashSet<String> ignore = new HashSet<String>();
    /** Skip making the picture (trying places). */
    private boolean probe;

    /**
     * Puts group id at the change want, or the nearest free place around it (up to radius art pixels away).
     * Returns false (and leaves the old change) if there is none. Lays the deck out again.
     */
    boolean placeNear(int w, int h, String id, int[] want, int radius) {
        HashMap<String, int[]> ed = edits(portraitLayout);
        int[] old = ed.get(id);
        probe = true;
        try {
            for (int r = 0; r <= radius; r++)
                for (int dy = -r; dy <= r; dy++)
                    for (int dx = -r; dx <= r; dx++) {
                        if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;   // this ring only
                        int[] e = {want[0] + dx, want[1] + dy, want[2]};
                        put(ed, id, e);
                        layout(w, h);
                        Group g = group(id);
                        if (!disabled.contains(id) && g != null && !g.bad) { probe = false; layout(w, h); return true; }
                    }
        } finally { probe = false; }
        put(ed, id, old);
        layout(w, h);
        return false;
    }

    private static void put(HashMap<String, int[]> ed, String id, int[] e) {
        if (e == null || (e[0] == 0 && e[1] == 0 && e[2] == 100)) ed.remove(id); else ed.put(id, e);
    }

    /**
     * Left-handed and back: every group mirrored left / right. Groups are placed one by one, biggest first,
     * each at its mirrored place or the nearest free one. Returns how many could not move.
     */
    int swapSides(int w, int h) {
        HashMap<String, int[]> ed = edits(portraitLayout);
        int axis2 = portraitLayout ? regions.get(0)[0] + regions.get(0)[2] : artW - 1;   // twice the mirror line
        ArrayList<Group> order = new ArrayList<Group>(groups);
        java.util.Collections.sort(order, new java.util.Comparator<Group>() {
            public int compare(Group a, Group b) { return (b.x1 - b.x0) * (b.y1 - b.y0) - (a.x1 - a.x0) * (a.y1 - a.y0); }
        });
        HashMap<String, int[]> want = new HashMap<String, int[]>();
        for (Group g : order) {
            int[] e = ed.containsKey(g.id) ? ed.get(g.id).clone() : new int[]{0, 0, 100};
            e[0] += axis2 - (g.x0 + g.x1);   // new centre = 2 axis - centre
            want.put(g.id, e);
        }
        int stuck = 0;
        ignore.clear();
        for (Group g : order) ignore.add(g.id);
        for (Group g : order) {
            ignore.remove(g.id);
            if (!placeNear(w, h, g.id, want.get(g.id), 24)) stuck++;
        }
        ignore.clear();
        layout(w, h);
        return stuck;
    }

    Group group(String id) { for (Group g : groups) if (g.id.equals(id)) return g; return null; }

    /** The group under the art pixel (x, y), or null (the selected one wins). */
    Group groupAt(float x, float y) {
        Group hit = null;
        for (Group g : groups) if (x >= g.x0 - 2 && x <= g.x1 + 2 && y >= g.y0 - 2 && y <= g.y1 + 2) { hit = g; if (g.id.equals(selected)) return g; }
        return hit;
    }

    /** Edit mode drawing: snap grid dots, dashed outlines; the selected group with handles and its size. */
    private void drawEditing(PixelArt a) {
        for (int[] r : regions)
            for (int y = r[1] + 1; y < r[3]; y += 4)
                for (int x = r[0] + 1; x < r[2]; x += 4) {
                    boolean free = true;
                    for (Group g : groups) if (x >= g.x0 - 1 && x <= g.x1 + 1 && y >= g.y0 - 1 && y <= g.y1 + 1) { free = false; break; }
                    if (free) a.set(x, y, D5);
                }
        for (Group g : groups) {
            boolean sel = g.id.equals(selected);
            int c = g.bad ? R3 : sel ? OR2 : BONE2;
            int x0 = g.x0 - 2, y0 = g.y0 - 2, x1 = g.x1 + 2, y1 = g.y1 + 2;
            for (int x = x0; x <= x1; x++) if (((x - x0) / 2) % 2 == 0) { a.set(x, y0, c); a.set(x, y1, c); }
            for (int y = y0; y <= y1; y++) if (((y - y0) / 2) % 2 == 0) { a.set(x0, y, c); a.set(x1, y, c); }
            if (sel) {
                int[][] cs = {{x0, y0}, {x1, y0}, {x0, y1}, {x1, y1}};
                for (int[] p : cs) { a.rect(p[0] - 2, p[1] - 2, p[0] + 2, p[1] + 2, K); a.rect(p[0] - 1, p[1] - 1, p[0] + 1, p[1] + 1, c); }
                String t = groupName(g.id);
                if (g.sizable) { int[] e = edits(portraitLayout).get(g.id); t += " " + (e != null ? e[2] : 100) + "%"; }
                int tw = PixelArt.textWidth(t, 1), tx = Math.max(1, Math.min(artW - tw - 1, g.cx() - tw / 2));
                int ty = y1 + 4 + 5 < artH ? y1 + 4 : y0 - 9;
                a.rect(tx - 1, ty - 1, tx + tw, ty + 5, K);
                a.text(t, tx, ty, c, 1);
            }
        }
    }

    /** Number keys for door codes inside kpX0..kpY1 (5 rows: 4 of keys, then CLOSE when wanted). */
    private void makeKeypad(boolean withClose) {
        String k = "123456789*0#";
        int rows = withClose ? 5 : 4;
        int cw = (kpX1 - kpX0 + 1) / 3, ch = (kpY1 - kpY0 + 1) / rows;
        for (int i = 0; i < 12; i++) {
            Btn b = new Btn(); b.kind = KEY; b.key = k.charAt(i); b.text = String.valueOf(k.charAt(i));
            b.x0 = kpX0 + (i % 3) * cw + 2; b.y0 = kpY0 + (i / 3) * ch + 2;
            b.x1 = b.x0 + cw - 5; b.y1 = b.y0 + ch - 5;
            keypad.add(b);
        }
        if (withClose) {
            Btn close = new Btn(); close.kind = KEY; close.key = K_CLOSE; close.text = "CLOSE";
            close.x0 = kpX0 + 2; close.y0 = kpY0 + 4 * ch + 2; close.x1 = kpX0 + 3 * cw - 3; close.y1 = close.y0 + ch - 5;
            keypad.add(close);
        }
    }

    /** What the physical buttons do (Android gamepad names): button, action. */
    static final String[][] LEGEND = {{"A", "FIRE"}, {"B", "MENU"}, {"START", "SETUP"}, {"X", "WAIT"}, {"Y", "MAP"},
            {"L R", "STEP"}, {"L2 R2", "WEAPON"}, {"SELECT", "KEYPAD"}, {"DPAD", "MOVE"}};

    /**
     * Handheld: the game in the middle at a whole-number scale (sharp pixels), a narrow panel on each side:
     * menu / map / keypad / wait buttons on the left, the button guide (or the keypad) on the right.
     * Returns false when there is no room at the sides (then the touch layout is used).
     */
    private boolean layoutHandheld(int w, int h) {
        float s = Math.min((float) w / gW, (float) h / gH);
        if (s >= 1) s = (float) Math.floor(s);
        float gw = gW * s, gh = gH * s, side = (w - gw) / 2;
        if (side < 24 * dp) return false;
        handheldLayout = true;
        setGame((w - gw) / 2, (h - gh) / 2, (w + gw) / 2, (h + gh) / 2);
        artPx = Math.max(1f, Math.min(Math.min(side / 64f, h / 140f), 3.2f * dp));
        artW = (int) Math.ceil(w / artPx); artH = (int) Math.ceil(h / artPx);
        int gl = (int) Math.floor(game[0] / artPx), gr = (int) Math.ceil(game[2] / artPx);
        panels.add(new int[]{0, 0, gl - 1, artH - 1});
        panels.add(new int[]{gr, 0, artW - 1, artH - 1});
        int lx = gl / 2, top = 0;
        if (xpOn && gl >= 30) {
            // XP bar at the top of the left panel; the buttons go below it.
            // Tall panels: LEVEL, the socket, the bar, then the numbers. Short ones: socket and bar side by side.
            int sc = gl >= 56 ? 2 : 1, lineH = sc == 2 ? 14 : 8;
            int sockR = sc == 2 ? 11 : 7, sockY = 6 + 5 * sc + 4 + sockR, barY = sockY + sockR + 7;
            int fullH = barY + 13 + 2 * lineH + 5 * sc + 4;
            if (artH - fullH >= 5 * (2 * 8 + 6) + 6) {
                xpHand = 1; xpScale = sc; xpSockX = lx; xpSockY = sockY; xpSockR = sockR;
                xpY0 = barY; xpInner = 5; xpX0 = 3; xpX1 = gl - 4;
                xpTextX = lx; xpTextY = barY + 13; xpTextLines = 3;
                top = fullH;
            } else {
                xpHand = 2; xpScale = 1; xpSockR = 7; xpSockX = 3 + xpSockR; xpSockY = 13;
                xpY0 = 8; xpInner = 6; xpX0 = xpSockX + xpSockR + 2; xpX1 = gl - 4; xpInBar = true;
                xpTextX = lx; xpTextY = 23; xpTextLines = 1;
                top = 31;
            }
        }
        int r = Math.max(5, Math.min(Math.min(12, gl / 2 - 6), ((artH - top - 8) / 5 - 6) / 2)), step = 2 * r + 6;
        int y = Math.max(top + r + 6, top + (artH - top) / 2 - step * 2);
        disc(K_SETTINGS, lx, y, r, Icons.GEAR);
        y += step;
        disc(K_MENU, lx, y, r, Icons.MENU).longPress = true;
        disc(K_MAP, lx, y + step, r, Icons.MAP);
        disc(0, lx, y + 2 * step, r, Icons.KEYS);
        disc(K_WAIT, lx, y + 3 * step, r, Icons.HOUR).repeat = false;
        // right panel: guide, or the keypad when it is open
        int rw = artW - gr;
        legend = PadBinds.legend();
        legendX = gr + 4; legendW = rw - 8;
        legendY = 4; legendH = artH - 8;
        kpX0 = gr + 3; kpX1 = artW - 4;
        int cw = Math.max(10, (kpX1 - kpX0 + 1) / 3), ch = Math.min(cw, (artH - 10) / 5);
        kpY0 = Math.max(5, (artH - 5 * ch) / 2); kpY1 = kpY0 + 5 * ch - 1;
        if (rw < 40) legend = null;
        makeKeypad(true);
        art = new PixelArt(artW, artH);
        return true;
    }

    /** Handheld mode, number keys open: the game moves left and shrinks a little, the right side is a big keypad. */
    private boolean layoutBigKeypad(int w, int h) {
        artPx = Math.max(1f, Math.min(h / 192f, 4f * dp));
        artW = (int) Math.ceil(w / artPx); artH = (int) Math.ceil(h / artPx);
        int kw = Math.min(artW / 2, (int) (artH * 0.62f)), split = artW - kw, hintH = 34;
        float s = Math.min((split - 8) * artPx / gW, (h - (hintH + 8) * artPx) / gH);
        if (s < 0.75f || kw < 60) return false;
        float gw = gW * s, gh = gH * s, gx = (split * artPx - gw) / 2, gy = (h - hintH * artPx - gh) / 2;
        setGame(gx, gy, gx + gw, gy + gh);
        handheldLayout = true; bigKeypad = true;
        panels.add(new int[]{0, 0, split - 1, artH - 1});
        panels.add(new int[]{split, 0, artW - 1, artH - 1});
        hintX = Math.max(6, (int) Math.ceil(gx / artPx)); hintY = (int) Math.ceil((gy + gh) / artPx) + 6;
        kpX0 = split + 4; kpX1 = artW - 5; kpY0 = 5; kpY1 = artH - 5;
        makeKeypad(true);
        if (cursor < 0 || cursor >= keypad.size()) cursor = 4;
        art = new PixelArt(artW, artH);
        return true;
    }

    /** Moves the controller cursor on the big keypad (3 x 4 keys, then CLOSE across the bottom). */
    private int cursorCol = 1;
    void moveCursor(int dx, int dy) {
        int c = cursor;
        if (c == 12) {
            if (dy < 0) c = 9 + cursorCol;
        } else {
            int r = c / 3, col = c % 3;
            if (dx < 0 && col > 0) col--;
            else if (dx > 0 && col < 2) col++;
            if (dy < 0 && r > 0) r--;
            else if (dy > 0) r++;
            cursorCol = col;
            c = r > 3 ? 12 : r * 3 + col;
        }
        cursor = c;
    }

    boolean blocked(Btn b) {
        if (!keypadOn || b.key == 0) return false;
        int x = b.centerX(), y = b.centerY();
        return x >= kpX0 && x <= kpX1 && y >= kpY0 && y <= kpY1;
    }

    // ---------------------------------------------------------------- XP bar
    private static final int[] GOLD = {0xFFFFE69A, 0xFFF2B544, 0xFFD08A22, 0xFFA2641A, 0xFF6E4410};
    private static final int TICK_ON = 0xFF5A360E, GOLD_TEXT = 0xFF2A1804, GOLD_EDGE = 0xFFFFF4D0;

    /** Level + XP as words: "53 / 80 XP", "27 TO LEVEL 2". */
    String xpNumbers(boolean xpWord) { return xp + " / " + xpNext + (xpWord ? " XP" : ""); }
    String xpToGo() { return (xpNext - xp) + " TO LEVEL " + (xpLevel + 1); }

    private void drawXp(PixelArt a) {
        boolean known = xpLevel > 0 && xpNext > 0;
        // frame: iron edge, dark groove
        int y1 = xpY0 + xpInner + 3, gx0 = xpX0 + 2, gx1 = xpX1 - 2, gy0 = xpY0 + 2, gy1 = y1 - 2;
        a.rect(xpX0, xpY0, xpX1, y1, K);
        a.hline(xpX0 + 1, xpX1 - 1, xpY0 + 1, D5); a.vline(xpX0 + 1, xpY0 + 1, y1 - 1, D5);
        a.hline(xpX0 + 1, xpX1 - 1, y1 - 1, D2); a.vline(xpX1 - 1, xpY0 + 1, y1 - 1, D2);
        a.rect(gx0, gy0, gx1, gy1, D1); a.hline(gx0, gx1, gy0, K);
        int w = gx1 - gx0 + 1, fill = known ? Math.min(w, Math.round(w * (float) xp / xpNext)) : 0;
        for (int y = gy0; y <= gy1; y++) {
            int k = (y - gy0) * (GOLD.length - 1) / Math.max(1, gy1 - gy0);
            if (fill > 0) a.hline(gx0, gx0 + fill - 1, y, GOLD[k]);
        }
        // portrait: everything in the bar; landscape and short handheld panels: the numbers in the bar,
        // "to next level" under it; tall handheld panels: the numbers under the bar
        String label = null;
        if (known && xpInBar) {
            label = xpTextLines == 0 ? xpNumbers(true) + "   " + xpToGo() : xpNumbers(true);
            if (PixelArt.textWidth(label, 1) > w - 4) label = xpNumbers(false);
            if (PixelArt.textWidth(label, 1) > w - 4) label = xp + "/" + xpNext;
            if (PixelArt.textWidth(label, 1) > w - 4) label = null;
        }
        int lx0 = -1, lx1 = -1, tw = label == null ? 0 : PixelArt.textWidth(label, 1);
        if (label != null) { lx0 = (gx0 + gx1) / 2 - tw / 2 - 3; lx1 = lx0 + tw + 5; }
        // bright leading edge, except behind the numbers
        if (fill > 0 && fill < w && (gx0 + fill - 1 < lx0 || gx0 + fill - 1 > lx1)) a.vline(gx0 + fill - 1, gy0, gy1, GOLD_EDGE);
        // ten segments, like Diablo's bar (no notch behind the numbers)
        for (int t = 1; t < 10; t++) {
            int x = gx0 + w * t / 10;
            if (x >= lx0 && x <= lx1) continue;
            a.vline(x, gy0 + 1, gy1, x < gx0 + fill ? TICK_ON : D3);
        }
        if (label != null) {
            // dark letters over the gold, light ones over the empty part
            int tx = (gx0 + gx1) / 2 - tw / 2, ty = gy0 + (gy1 - gy0 + 1 - 5) / 2 + 1, edge = gx0 + fill;
            for (int k = 0; k < label.length(); k++) {
                String[] g = Icons.glyph(label.charAt(k));
                for (int j = 0; j < g.length; j++)
                    for (int i = 0; i < g[j].length(); i++)
                        if (g[j].charAt(i) == '#') a.set(tx + i, ty + j, tx + i < edge ? GOLD_TEXT : BONE);
                tx += g[0].length() + 1;
            }
        }
        // level socket: iron ring, dark red core, the level in gold
        if (xpSockR > 0) {
            String lv = known ? String.valueOf(xpLevel) : "-";
            int cx = xpSockX, cy = xpSockY, r = Math.max(xpSockR, PixelArt.textWidth(lv, xpScale) / 2 + 3 + xpScale);
            disc(a, cx, cy, r, K); disc(a, cx, cy, r - 1, D5); disc(a, cx + 1, cy + 1, r - 1, D2); disc(a, cx, cy, r - 2, D4);
            disc(a, cx, cy, r - 3, R1); disc(a, cx - 1, cy - 1, r - 4, R2);
            int sc = xpScale, lw = PixelArt.textWidth(lv, sc);
            a.text(lv, cx - lw / 2 + sc, cy - 5 * sc / 2 + sc, K, sc);
            a.text(lv, cx - lw / 2, cy - 5 * sc / 2, OR2, sc);
        }
        if (xpHand == 1) a.text("LEVEL", xpTextX - PixelArt.textWidth("LEVEL", xpScale) / 2, 6, OR, xpScale);
        if (!known || xpTextLines == 0) return;
        if (xpHand == 1) {
            // tall handheld panel: the numbers under the bar
            int sc = xpScale, lineH = xpLineH();
            String[] lines = {xp + "/" + xpNext, (xpNext - xp) + " TO", "LEVEL " + (xpLevel + 1)};
            int[] cols = {OR2, BONE, BONE};
            for (int i = 0; i < xpTextLines; i++)
                a.text(lines[i], xpTextX - PixelArt.textWidth(lines[i], sc) / 2, xpTextY + i * lineH, cols[i], sc);
            return;
        }
        // one line under the bar: right-aligned (landscape) or centred (short handheld panel)
        String t = xpToGo();
        int room = xpTextRight ? xpTextX - (xpSockX - xpSockR) : xpX1 - 1;
        if (PixelArt.textWidth(t, 1) > room) t = (xpNext - xp) + " TO GO";
        if (PixelArt.textWidth(t, 1) > room) return;
        int tx = xpTextRight ? xpTextX - PixelArt.textWidth(t, 1) : xpTextX - PixelArt.textWidth(t, 1) / 2;
        a.text(t, tx + 1, xpTextY + 1, K, 1);
        a.text(t, tx, xpTextY, BONE2, 1);
    }

    private int xpLineH() { return xpScale == 2 ? 14 : 8; }

    private static void disc(PixelArt a, int cx, int cy, int r, int c) {
        if (r < 0) return;
        for (int y = cy - r; y <= cy + r; y++)
            for (int x = cx - r; x <= cx + r; x++)
                if ((x - cx) * (x - cx) + (y - cy) * (y - cy) <= r * r + r * 0.8f) a.set(x, y, c);
    }

    /** Draws the deck into art.px. */
    void render() {
        PixelArt a = art;
        a.clear(K);
        for (int i = 0; i < panels.size(); i++) {
            int[] p = panels.get(i);
            if (p[2] > p[0]) a.panel(p[0], p[1], p[2], p[3], 1234L + i);
        }
        if (label != null && !editing) a.text(label, labelX, labelY, BONE2, 1);
        if (xpX0 >= 0 && !(keypadOn && !handheldLayout && artW > artH)) drawXp(a);
        if (legend != null && !(mode == MODE_HANDHELD && keypadOn)) drawLegend(a);
        if (bigKeypad) {
            int kw = 0;
            String[] HINT = hint();
            for (int i = 0; i < HINT.length; i += 2) kw = Math.max(kw, PixelArt.textWidth(HINT[i], 1));
            for (int i = 0; i + 1 < HINT.length; i += 2) {
                a.text(HINT[i], hintX, hintY + i / 2 * 9, OR, 1);
                a.text(HINT[i + 1], hintX + kw + 6, hintY + i / 2 * 9, BONE, 1);
            }
        }
        boolean slotDrawn = false, wellDrawn = false;
        for (int i = 0; i < buttons.size(); i++) {
            Btn b = buttons.get(i);
            if (blocked(b)) continue;
            boolean on = b.pointer != -1 || (b.key == 0 && keypadOn);
            switch (b.kind) {
                case CELL:
                    if (!wellDrawn) { crossWell(a); wellDrawn = true; }
                    if (on) {
                        a.bevelBox(b.x0, b.y0 + 1, b.x1, b.y1 + 1, R2, R3, R1, true);
                        a.blitCenter(b.icon, (b.x0 + b.x1 + 1) / 2, (b.y0 + b.y1 + 1) / 2 + 1, OR2, true);
                    } else {
                        a.bevelBox(b.x0, b.y0, b.x1, b.y1, D4, D5, D2, false);
                        a.blitCenter(b.icon, (b.x0 + b.x1 + 1) / 2, (b.y0 + b.y1 + 1) / 2, BONE, true);
                    }
                    break;
                case DISC:
                    if (b.key == 0 && keypadOn)   // glowing ring: "keypad is open, tap here to close"
                        a.ellipse(b.cx - b.r - 3, b.cy - b.r - 3, b.cx + b.r + 3, b.cy + b.r + 4, pulse ? OR2 : OR);
                    if (on) a.pixDisc(b.cx, b.cy + 1, b.r, D5, BONE2, D3);
                    else a.pixDisc(b.cx, b.cy, b.r, D4, D5, D2);
                    a.blitCenter(b.icon, b.cx, b.cy + (on ? 1 : 0), on ? OR2 : BONE, true);
                    break;
                case FIRE:
                    if (on) { a.pixDisc(b.cx, b.cy + 1, b.r, R3, OR, R2); a.blitCenter(Icons.CROSS, b.cx, b.cy + 1, OR2, false); }
                    else { a.pixDisc(b.cx, b.cy, b.r, R2, R3, R1); a.blitCenter(Icons.CROSS, b.cx, b.cy, BONE, false); }
                    break;
                case SHOULDER: {
                    int dy = on ? 1 : 0;
                    if (on) a.bevelBox(b.x0, b.y0 + 1, b.x1, b.y1 + 1, R2, R3, R1, true);
                    else a.bevelBox(b.x0, b.y0, b.x1, b.y1, D4, D5, D2, false);
                    // rounded outer top corner, like a shoulder button
                    boolean left = b.key == K_STRAFE_L;
                    int ox0 = left ? b.x0 : b.x1, sgn = left ? 1 : -1;
                    a.set(ox0, b.y0 + dy, K); a.set(ox0 + sgn, b.y0 + dy, K); a.set(ox0, b.y0 + 1 + dy, K);
                    int sc = (b.y1 - b.y0) >= 13 ? 2 : 1;
                    int tw = PixelArt.textWidth(b.text, sc), iw = b.icon[0].length(), gapw = 3;
                    int tx = (b.x0 + b.x1 + 1) / 2 - (tw + gapw + iw) / 2, my = (b.y0 + b.y1 + 1) / 2 + dy;
                    int col = on ? OR2 : BONE;
                    if (left) { a.text(b.text, tx, my - (5 * sc) / 2, on ? OR2 : OR, sc); a.blitCenter(b.icon, tx + tw + gapw + iw / 2, my, col, true); }
                    else { a.blitCenter(b.icon, tx + iw / 2, my, col, true); a.text(b.text, tx + iw + gapw, my - (5 * sc) / 2, on ? OR2 : OR, sc); }
                    break;
                }
                case SLOT:
                    if (!slotDrawn) {
                        a.well(slotCx - 28, slotCy - 7, slotCx + 28, slotCy + 7);
                        slotDrawn = true;
                    }
                    if (b.key == K_WPN_PREV) {
                        a.blitCenter(Icons.CHEV_L, slotCx - 25, slotCy, on ? OR2 : R3, false);
                        if (wpnPrev >= 0) a.blitCenter(Icons.hflip(Icons.WEAPON_S[wpnPrev]), slotCx - 17, slotCy, on ? OR2 : BONE2, false);
                    } else if (b.key == K_WPN_NEXT) {
                        a.blitCenter(Icons.CHEV_R, slotCx + 25, slotCy, on ? OR2 : R3, false);
                        if (wpnNext >= 0) a.blitCenter(Icons.WEAPON_S[wpnNext], slotCx + 17, slotCy, on ? OR2 : BONE2, false);
                    } else {
                        a.blitCenter(wpnCur >= 0 ? Icons.WEAPON[wpnCur] : Icons.GUN, slotCx, slotCy, on ? OR2 : BONE, true);
                    }
                    break;
            }
        }
        if (editing && editable) drawEditing(a);
        if (keypadOn) {
            a.well(kpX0, kpY0, kpX1, kpY1);
            for (int i = 0; i < keypad.size(); i++) {
                Btn b = keypad.get(i);
                boolean on = b.pointer != -1;
                int dy = on ? 1 : 0;
                if (on) a.bevelBox(b.x0, b.y0 + 1, b.x1, b.y1 + 1, R2, R3, R1, true);
                else a.bevelBox(b.x0, b.y0, b.x1, b.y1, D4, D5, D2, false);
                int sc = (b.key != K_CLOSE && (b.y1 - b.y0) >= 12) ? 2 : 1;
                if (bigKeypad) sc = b.key == K_CLOSE ? ((b.y1 - b.y0) >= 20 ? 2 : 1) : Math.max(sc, Math.min(4, (b.y1 - b.y0) / 9));
                int tw = PixelArt.textWidth(b.text, sc);
                int col = on ? OR2 : (b.key == K_CLOSE ? OR : BONE);
                if (b.key == K_CLOSE) {   // "X CLOSE"
                    int tx = (b.x0 + b.x1 + 1) / 2 - (tw + 8) / 2, ty = (b.y0 + b.y1 + 1) / 2 - (5 * sc) / 2 + dy;
                    a.blit(Icons.X, tx, ty + (5 * sc) / 2 - 2, col, false);
                    a.text(b.text, tx + 8, ty, col, sc);
                } else
                    a.text(b.text, (b.x0 + b.x1 + 1) / 2 - tw / 2, (b.y0 + b.y1 + 1) / 2 - (5 * sc) / 2 + dy, col, sc);
            }
            if (bigKeypad && cursor >= 0 && cursor < keypad.size()) {   // controller cursor: an orange frame
                Btn b = keypad.get(cursor);
                for (int k = 1; k <= 3; k++) {
                    a.hline(b.x0 - 1 - k, b.x1 + 1 + k, b.y0 - 1 - k, OR2); a.hline(b.x0 - 1 - k, b.x1 + 1 + k, b.y1 + 2 + k, OR2);
                    a.vline(b.x0 - 1 - k, b.y0 - 1 - k, b.y1 + 2 + k, OR2); a.vline(b.x1 + 1 + k, b.y0 - 1 - k, b.y1 + 2 + k, OR2);
                }
            }
        }
    }

    /** Recessed plus-shaped bed for the cross, with the fixed centre piece. */
    private void crossWell(PixelArt a) {
        int c = crossCell, span = c * 3 + 2, x0 = crossCx - span / 2, y0 = crossCy - span / 2;
        int ax0 = x0 + c + 1 - 3, ax1 = x0 + 2 * c + 3, ay0 = y0 + c + 1 - 3, ay1 = y0 + 2 * c + 3;   // arm bounds
        a.well(ax0, y0 - 3, ax1, y0 + span + 2);          // vertical arm
        a.well(x0 - 3, ay0, x0 + span + 2, ay1);          // horizontal arm
        for (int x = ax0 + 1; x < ax1; x++) { a.set(x, ay0, PixelArt.D1); a.set(x, ay1, PixelArt.D1); }
        for (int y = ay0 + 1; y < ay1; y++) { a.set(ax0, y, PixelArt.D1); }
        int m0x = x0 + c + 1, m0y = y0 + c + 1;
        a.bevelBox(m0x, m0y, m0x + c - 1, m0y + c - 1, D3, D4, D2, false);   // centre: not a button
        a.ellipse(crossCx - 2, crossCy - 2, crossCx + 2, crossCy + 2, D2);
    }

    /** Button guide: one line per button if it fits (large, then small text), else button above action. */
    private void drawLegend(PixelArt a) {
        int n = legend.length;
        for (int sc = 2; sc >= 1; sc--) {
            int kw = 0, aw = 0;
            for (String[] e : legend) { kw = Math.max(kw, PixelArt.textWidth(e[0], sc)); aw = Math.max(aw, PixelArt.textWidth(e[1], sc)); }
            int gap = 3 * sc, lh = 5 * sc + 3 * sc;
            if (kw + gap + aw <= legendW && n * lh <= legendH) {            // "A   FIRE"
                int y = legendY + (legendH - n * lh) / 2;
                for (String[] e : legend) { a.text(e[0], legendX, y, OR, sc); a.text(e[1], legendX + kw + gap, y, BONE, sc); y += lh; }
                return;
            }
            int eh = 2 * (5 * sc) + 2 * sc + 3 * sc;
            if (Math.max(kw, aw) <= legendW && n * eh <= legendH) {          // "A" / "FIRE"
                int y = legendY + (legendH - n * eh) / 2;
                for (String[] e : legend) { a.text(e[0], legendX, y, OR, sc); a.text(e[1], legendX, y + 5 * sc + 2 * sc, BONE, sc); y += eh; }
                return;
            }
        }
        int y = legendY;                                                     // last resort: small, top-aligned
        for (String[] e : legend) { a.text(e[0] + " " + e[1], legendX, y, BONE, 1); y += 8; }
    }

    private void setGame(float l, float t, float r, float b) { game[0] = l; game[1] = t; game[2] = r; game[3] = b; }
}
