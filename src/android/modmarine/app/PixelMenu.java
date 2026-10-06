package modmarine.app;

import java.util.ArrayList;

import static modmarine.app.PixelArt.*;

/**
 * A window in the deck's pixel style: settings, choices, questions and notes. Pure Java: lays out and paints in
 * art pixels; GameView shows it over the game and passes taps and controller buttons to it.
 */
final class PixelMenu {
    /** Row kinds: a switch (on / off), a value that cycles, a row that opens something, a button, a read-only line, a radio choice. */
    static final int SWITCH = 0, CHOICE = 1, OPEN = 2, BUTTON = 3, INFO = 4, RADIO = 5, HEADING = 6;

    static final class Item {
        int kind = OPEN;
        String[] icon;
        String label = "", value = "", sub;
        boolean on, danger, gap;
        /** Picture shown on the right of a RADIO row (index into GameView's previews), or -1. */
        int preview = -1;
        Runnable act, left, right;
        boolean selectable() { return kind != INFO && kind != HEADING; }
    }

    static final int W = 124, PAD = 6;
    String title = "", corner = "";
    final ArrayList<String> message = new ArrayList<String>();
    final ArrayList<Item> items = new ArrayList<Item>();
    String hintTouch = "", hintPad = "";
    int cursor = -1;
    /** The touch-controls editor: sits over the game picture only; touches outside it go to the deck. */
    boolean editor;
    /** Called when the window is closed with B / Back / a tap outside. */
    Runnable onClose;

    Item add(int kind, String[] icon, String label, String value, Runnable act) {
        Item it = new Item(); it.kind = kind; it.icon = icon; it.label = label; it.value = value == null ? "" : value; it.act = act;
        items.add(it);
        return it;
    }

    /** Message text, wrapped to the window (the pixel font has capitals only). */
    void say(String text) {
        for (String para : text.toUpperCase().split("\n")) {
            if (para.trim().isEmpty()) { message.add(""); continue; }
            StringBuilder line = new StringBuilder();
            for (String word : para.trim().split(" +")) {
                String t = line.length() == 0 ? word : line + " " + word;
                if (PixelArt.textWidth(t, 1) > W - 2 * PAD - 2 && line.length() > 0) { message.add(line.toString()); line.setLength(0); line.append(word); }
                else { line.setLength(0); line.append(t); }
            }
            if (line.length() > 0) message.add(line.toString());
        }
    }

    // ---------------------------------------------------------------- layout (art pixels inside the window)
    int rowH(Item it) {
        switch (it.kind) { case RADIO: return 17; case INFO: return 8; case HEADING: return 10; default: return 11; }
    }
    int messageTop() { return 14; }
    int itemsTop() { return messageTop() + (message.isEmpty() ? 0 : message.size() * 7 + 4); }
    int rowTop(int i) {
        int y = itemsTop();
        for (int k = 0; k < i; k++) { Item it = items.get(k); y += rowH(it) + (it.kind == INFO ? 0 : 1); if (items.get(k + 1).gap) y += 4; }
        return y;
    }
    int height() {
        int y = itemsTop();
        if (!items.isEmpty()) { int last = items.size() - 1; y = rowTop(last) + rowH(items.get(last)) + 1; }
        return y + (hintTouch.isEmpty() && hintPad.isEmpty() ? 4 : 15);
    }

    /** Where a RADIO row's picture goes: x0, y0, x1, y1 (inclusive). */
    int[] previewRect(int i) {
        int y = rowTop(i);
        return new int[]{W - PAD - 34, y + 2, W - PAD - 3, y + rowH(items.get(i)) - 3};
    }

    /** Row under the window art pixel (x, y), or -1. */
    int itemAt(float x, float y) {
        for (int i = 0; i < items.size(); i++) {
            int t = rowTop(i);
            if (items.get(i).selectable() && x >= PAD - 1 && x <= W - PAD + 1 && y >= t - 1 && y <= t + rowH(items.get(i))) return i;
        }
        return -1;
    }

    /** A value row with both a "less" and a "more" (drawn "< value >"). */
    static boolean twoWay(Item it) { return it.kind == CHOICE && it.left != null && it.right != null; }

    /**
     * Which arrow of row i the window art pixel x is on: -1 the left half of "< value >" (less), +1 the right half
     * (more), 0 elsewhere on the row (the label).
     */
    int arrowAt(int i, float x) {
        if (i < 0 || i >= items.size() || !twoWay(items.get(i))) return 0;
        Item it = items.get(i);
        int x1 = W - PAD + 1, tw = PixelArt.textWidth(it.value, 1);
        int v0 = x1 - 15 - tw - 8, mid = x1 - 9 - tw / 2;   // some room left of the "<" for a finger
        if (x < v0) return 0;
        return x < mid ? -1 : 1;
    }

    /** How far the rows are scrolled (art pixels), when the window is taller than the screen. */
    int scroll;
    /** Where the rows start (art pixels, room for a gap line above the first), and where they end. */
    int bodyTop() { return itemsTop() - 4; }
    int bodyEnd() { return items.isEmpty() ? itemsTop() : rowTop(items.size() - 1) + rowH(items.get(items.size() - 1)) + 2; }

    void move(int d) {
        if (items.isEmpty()) return;
        int n = items.size(), c = cursor < 0 ? (d > 0 ? -1 : n) : cursor;
        for (int k = 0; k < n; k++) { c = (c + d + n) % n; if (items.get(c).selectable()) { cursor = c; return; } }
    }

    void firstSelectable() { cursor = -1; move(1); }

    // ---------------------------------------------------------------- painting
    PixelArt paint(boolean controller) {
        int H = height();
        PixelArt a = new PixelArt(W + 2, H + 2);
        a.bevelBox(1, 1, W, H, D3, D5, D1, false);
        a.text(title, PAD + 1, 5, OR, 1);
        if (!corner.isEmpty()) a.text(corner, W - PAD + 1 - PixelArt.textWidth(corner, 1), 5, D5, 1);
        int y = messageTop();
        for (String line : message) { a.text(line, PAD + 1, y, BONE, 1); y += 7; }
        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i);
            int t = rowTop(i), h = rowH(it), x0 = PAD, x1 = W - PAD + 1;
            if (it.gap) a.hline(PAD + 1, W - PAD, t - 3, D2);
            boolean sel = i == cursor;
            switch (it.kind) {
                case HEADING:
                    a.text(it.label, PAD + 1, t + 3, OR, 1);
                    break;
                case INFO: {
                    a.text(it.label, PAD + 3, t + 1, BONE2, 1);
                    a.text(it.value, x1 - 2 - PixelArt.textWidth(it.value, 1), t + 1, BONE, 1);
                    break;
                }
                case BUTTON: {
                    if (sel) a.bevelBox(x0, t, x1, t + h - 1, R2, OR2, R1, false); else a.bevelBox(x0, t, x1, t + h - 1, D4, D5, D2, false);
                    int tw = PixelArt.textWidth(it.label, 1);
                    a.text(it.label, (x0 + x1) / 2 - tw / 2, t + 3, sel ? OR2 : (it.danger ? R3 : BONE), 1);
                    break;
                }
                case RADIO: {
                    if (sel) a.bevelBox(x0, t, x1, t + h - 1, R2, OR2, R1, false); else a.bevelBox(x0, t, x1, t + h - 1, D4, D5, D2, false);
                    a.ellipse(x0 + 4, t + 5, x0 + 10, t + 11, K); a.ellipse(x0 + 5, t + 6, x0 + 9, t + 10, D1);
                    if (it.on) a.ellipse(x0 + 6, t + 7, x0 + 8, t + 9, OR2);
                    a.text(it.label, x0 + 14, t + 3, sel ? OR2 : BONE, 1);
                    if (it.sub != null) a.text(it.sub, x0 + 14, t + 10, sel ? BONE : BONE2, 1);
                    if (it.preview >= 0) { int[] r = previewRect(i); a.rect(r[0] - 1, r[1] - 1, r[2] + 1, r[3] + 1, K); }
                    break;
                }
                default: {
                    if (sel) a.bevelBox(x0, t, x1, t + h - 1, R2, OR2, R1, false); else a.bevelBox(x0, t, x1, t + h - 1, D4, D5, D2, false);
                    if (it.icon != null) a.blitCenter(it.icon, x0 + 8, t + h / 2, sel ? OR2 : BONE2, false);
                    a.text(it.label, x0 + 16, t + 3, sel ? OR2 : (it.danger ? R3 : BONE), 1);
                    if (it.kind == SWITCH) {
                        int sx1 = x1 - 3, sx0 = sx1 - 14;
                        a.rect(sx0, t + 2, sx1, t + h - 3, K); a.rect(sx0 + 1, t + 3, sx1 - 1, t + h - 4, it.on ? R2 : D2);
                        int k = it.on ? sx1 - 6 : sx0 + 1; a.rect(k, t + 3, k + 5, t + h - 4, it.on ? OR2 : D5);
                    } else if (it.kind == CHOICE) {
                        int tw = PixelArt.textWidth(it.value, 1);
                        if (twoWay(it)) {   // "< value >": both arrows can be tapped
                            a.text(it.value, x1 - 9 - tw, t + 3, OR, 1);
                            a.blitCenter(Icons.CHEV_L, x1 - 13 - tw - 2, t + h / 2, sel ? OR2 : R3, false);
                            a.blitCenter(Icons.CHEV_R, x1 - 5, t + h / 2, sel ? OR2 : R3, false);
                        } else {
                            a.text(it.value, x1 - 3 - tw, t + 3, OR, 1);
                            a.blitCenter(Icons.CHEV_L, x1 - 7 - tw - 2, t + h / 2, R3, false);
                        }
                    } else {
                        if (!it.value.isEmpty()) {
                            int tw = PixelArt.textWidth(it.value, 1);
                            a.text(it.value, x1 - 9 - tw, t + 3, OR, 1);
                        }
                        a.blitCenter(Icons.CHEV_R, x1 - 5, t + h / 2, sel ? OR2 : D5, false);
                    }
                }
            }
        }
        String hint = controller ? hintPad : hintTouch;
        if (!hint.isEmpty()) a.text(hint, 1 + W / 2 - PixelArt.textWidth(hint, 1) / 2, H - 9, BONE2, 1);
        return a;
    }
}
