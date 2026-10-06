package modmarine.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;



/** Renders the game's screen scaled up, plus a pixel-art touch-control deck. */
final class GameView extends View {
    // control codes (KeyPump turns them into BREW keys): arrows, select, the two soft keys, CLR and phone keys
    static final int K_UP = -1, K_DOWN = -2, K_LEFT = -3, K_RIGHT = -4, K_FIRE = -5,
            K_MENU = -6, K_MAP = -7, K_CLR = -8, K_STRAFE_L = '1', K_STRAFE_R = '3',
            K_WPN_PREV = '*', K_WPN_NEXT = '7', K_WAIT = '9';
    /** Not a game key: opens / closes the number keys (Select on a controller). */
    static final int K_KEYPAD = -20;
    /** Not a game key: opens Stout Marine's settings (the gear button; Start on a controller). */
    static final int K_SETTINGS = -21;
    /** Not a game key: opens / closes the weapon picker (the weapon in the middle of the slot). */
    static final int K_WEAPONS = -22;


    final KeyPump keys;
    /** Stout Marine's settings: the gear button, or holding the menu button. */
    Runnable settings;
    private final float dp;

    // framebuffer
    private final Object frameLock = new Object();
    private int[] front;
    private int fw, fh;
    private boolean dirty;
    private Bitmap bmp;
    private final Rect src = new Rect();
    private final RectF gameRect = new RectF();
    private final Paint bmpPaint = new Paint();
    /** Picture setting: Prefs.PICTURE_SHARP / SMOOTH / HQ / XBR. */
    private volatile int picture;
    private volatile Upscaler upscaler;
    private Bitmap upBmp;
    private volatile int upFactor = 2;

    // pixel deck
    private final Deck deck;
    private Bitmap deckBmp;
    private boolean deckDirty = true;
    private final Paint deckPaint = new Paint();
    private final RectF deckDst = new RectF();
    private boolean hidden; // hidden while a gamepad / keyboard is in use

    // swipe on the game picture
    private int swPointer = -1;
    private float swX, swY;
    private long swAt;
    private boolean swDone;
    private int swKey;
    private long swLastRepeat;
    private long lastPulse;

    GameView(Context c, KeyPump keys, int gameW, int gameH) { this(c, keys, gameW, gameH, Deck.MODE_TOUCH); }

    /** mode: Deck.MODE_TOUCH / MODE_HANDHELD (game + side panels). */
    GameView(Context c, KeyPump keys, int gameW, int gameH, int mode) {
        super(c);
        this.keys = keys;
        dp = c.getResources().getDisplayMetrics().density;
        deck = new Deck(dp, gameW, gameH);
        deck.mode = mode;
        deck.xpOn = Prefs.xpBar(c);
        Deck.loadEdits(Prefs.touchEdits(c, true), deck.editsP);
        Deck.loadEdits(Prefs.touchEdits(c, false), deck.editsL);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setKeepScreenOn(true);
        setHapticFeedbackEnabled(true);
        picture = Prefs.picture(c);
        deckPaint.setFilterBitmap(false);
        deckPaint.setAntiAlias(false);
        mapPaint.setFilterBitmap(false);
        mapPaint.setAlpha(210);
        post(ticker);
        setWeaponArt(null);   // plain icons until the game's pictures are read
    }

    // ---------------------------------------------------------------- frames
    void submitFrame(int[] argb, int w, int h) {
        synchronized (frameLock) {
            if (front == null || front.length != argb.length) front = new int[argb.length];
            System.arraycopy(argb, 0, front, 0, argb.length);
            fw = w; fh = h; dirty = true;
        }
        Upscaler u = upscaler;
        int pic = picture;
        if (u != null && (pic == Prefs.PICTURE_HQ || pic == Prefs.PICTURE_XBR)) u.submit(argb, w, h, pic, upFactor);
        postInvalidateOnAnimation();
    }

    int picture() { return picture; }

    // mini-map (drawn small and see-through in the top-right corner of the game picture)
    private int[] mapFront;
    private int mapSize;
    private boolean mapDirty;
    private Bitmap mapBmp;
    private final Paint mapPaint = new Paint();
    private final RectF mapDst = new RectF();

    /** A new mini-map picture (size x size), or null to hide it. Called on the emulator thread. */
    void submitMap(int[] px, int size) { submitMap(px, size, false); }

    private volatile boolean mapBig;

    /** big: the larger, more see-through map. */
    void submitMap(int[] px, int size, boolean big) {
        mapBig = big;
        synchronized (frameLock) {
            if (px == null) { mapSize = 0; }
            else {
                if (mapFront == null || mapFront.length != px.length) mapFront = new int[px.length];
                System.arraycopy(px, 0, mapFront, 0, px.length);
                mapSize = size;
            }
            mapDirty = true;
        }
        postInvalidateOnAnimation();
    }

    /** Changes the Picture setting (and saves it). */
    void setPicture(int p) {
        picture = p;
        Prefs.setPicture(getContext(), p);
        if (p == Prefs.PICTURE_HQ || p == Prefs.PICTURE_XBR) {
            if (upscaler == null) { upscaler = new Upscaler(this); upscaler.start(); }
            upscaler.clear();
            synchronized (frameLock) { if (front != null) upscaler.submit(front, fw, fh, p, upFactor); }
        }
        invalidate();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (upscaler == null && (picture == Prefs.PICTURE_HQ || picture == Prefs.PICTURE_XBR)) { upscaler = new Upscaler(this); upscaler.start(); }
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (upscaler != null) { upscaler.quit(); upscaler = null; }
    }

    // ---------------------------------------------------------------- layout
    /** Switches between the touch deck and handheld mode (re-lays out at once). */
    void setMode(int mode) {
        if (deck.mode == mode) return;
        deck.mode = mode;
        deck.keypadOn = false;
        if (getWidth() > 0) onSizeChanged(getWidth(), getHeight(), getWidth(), getHeight());
        if (mode != Deck.MODE_TOUCH) hidden = false;
        invalidate();
    }

    int mode() { return deck.mode; }

    /** Lays the deck out again (after a setting that changes it). */
    void relayout() { if (getWidth() > 0) onSizeChanged(getWidth(), getHeight(), getWidth(), getHeight()); invalidate(); }

    // ---------------------------------------------------------------- touch-controls editor
    /** Edit mode is possible: the touch layout (not handheld). */
    boolean canEditTouch() { return deck.editable && deck.mode == Deck.MODE_TOUCH && !deck.handheldLayout; }
    private PixelMenu editMenu;
    private int editPointer = -1;
    private boolean editOnWindow;
    private float editX, editY;
    private int[] editBase;

    /** Opens the editor (from the settings). */
    void startEditing() {
        if (!canEditTouch()) return;
        closeAllMenus();
        setKeypad(false);
        deck.editing = true; deck.selected = null; deck.dragging = null;
        editMenu = new PixelMenu(); editMenu.editor = true;
        editMenu.onClose = new Runnable() { public void run() { stopEditing(); } };
        fillEditMenu();
        openMenu(editMenu, false);
        editMenu.cursor = -1;
        changed();
    }

    private void stopEditing() {
        deck.editing = false; deck.selected = null; deck.dragging = null; editMenu = null; editPointer = -1;
        saveEdits();
        relayout();
    }

    private java.util.HashMap<String, int[]> curEdits() { return deck.edits(deck.portraitLayout); }

    private void saveEdits() {
        Prefs.setTouchEdits(getContext(), true, Deck.saveEdits(deck.editsP));
        Prefs.setTouchEdits(getContext(), false, Deck.saveEdits(deck.editsL));
    }

    private void fillEditMenu() {
        final PixelMenu m = editMenu;
        if (m == null) return;
        int cur = m.cursor;
        m.items.clear(); m.message.clear();
        m.title = "EDIT TOUCH CONTROLS"; m.corner = deck.portraitLayout ? "PORTRAIT" : "LANDSCAPE";
        m.say("Drag a group to move it. Tap one to choose it.");
        final Deck.Group g = deck.selected != null ? deck.group(deck.selected) : null;
        if (g != null && g.sizable) {
            int[] e = curEdits().get(g.id);
            final int size = e != null ? e[2] : 100;
            PixelMenu.Item it = m.add(PixelMenu.CHOICE, Icons.I_SCR, "SIZE: " + Deck.groupName(g.id), size + "%", null);
            it.left = new Runnable() { public void run() { resize(g.id, size - 10, true); } };
            it.right = new Runnable() { public void run() { resize(g.id, size + 10, true); } };
            it.act = new Runnable() { public void run() { bounceSize(g.id, size); } };
        } else m.add(PixelMenu.INFO, null, g == null ? "NOTHING CHOSEN" : Deck.groupName(g.id), g == null ? "" : "SAME SIZE", null);
        m.add(PixelMenu.OPEN, Icons.I_REDO, "SWAP LEFT / RIGHT", null, new Runnable() { public void run() { swapSides(); } });
        m.add(PixelMenu.BUTTON, null, "RESET TO DEFAULT", null, new Runnable() { public void run() {
            curEdits().clear(); deck.selected = null; relayout(); saveEdits(); fillEditMenu(); toast("Touch controls reset."); } }).gap = true;
        m.add(PixelMenu.BUTTON, null, "DONE", null, new Runnable() { public void run() { closeMenu(); } });
        m.cursor = cur < m.items.size() ? cur : -1;
        menuChanged();
    }

    /** Which way tapping the size row goes next (+1 bigger, -1 smaller); it turns round at the ends. */
    private int sizeDir = 1;

    /** Changes a group's size; a size with no room (or out of range) is not kept. Returns true if it changed. */
    private boolean resize(String id, int size, boolean tell) {
        boolean ok = false;
        if (size >= Deck.SIZE_MIN && size <= Deck.sizeMax(id)) {
            int[] old = curEdits().get(id);
            int[] e = old != null ? old.clone() : new int[]{0, 0, 100};
            e[2] = size;
            ok = deck.placeNear(getWidth(), getHeight(), id, e, 8);
        }
        if (!ok && tell) toast(size > Deck.sizeMax(id) ? "That's the biggest size." : size < Deck.SIZE_MIN ? "That's the smallest size." : "No room for that size here.");
        relayout(); saveEdits(); fillEditMenu();
        return ok;
    }

    /** Tapping the size row: up a step until the biggest size that fits, then back down to the smallest, and so on. */
    private void bounceSize(String id, int size) {
        if (resize(id, size + 10 * sizeDir, false)) return;
        sizeDir = -sizeDir;
        resize(id, size + 10 * sizeDir, true);
    }

    /** Mirrors every group left / right (left-handed and back); each goes to its mirrored place or the nearest free one. */
    private void swapSides() {
        int stuck = deck.swapSides(getWidth(), getHeight());
        relayout(); saveEdits(); fillEditMenu();
        if (stuck > 0) toast("Some controls had no room on the other side.");
    }

    private void editorTouch(MotionEvent e) {
        int act = e.getActionMasked(), idx = e.getActionIndex();
        float x = e.getX(idx), y = e.getY(idx);
        if (act == MotionEvent.ACTION_DOWN || act == MotionEvent.ACTION_POINTER_DOWN) {
            if (editPointer != -1) return;
            editPointer = e.getPointerId(idx);
            editOnWindow = menuDst.contains(x, y);
            if (editOnWindow) { menuTouchEvent(act, x, y); return; }
            Deck.Group g = deck.groupAt(x / deck.artPx, y / deck.artPx);
            if (g == null || !g.id.equals(deck.selected)) sizeDir = 1;   // a new choice: tapping its size starts upwards
            deck.selected = g != null ? g.id : null;
            deck.dragging = g != null ? g.id : null;
            if (g != null) {
                int[] base = curEdits().get(g.id);
                editBase = base != null ? base.clone() : new int[]{0, 0, 100};
                editX = x; editY = y;
                haptic();
            }
            fillEditMenu(); changed();
        } else if (act == MotionEvent.ACTION_MOVE) {
            int pi = e.findPointerIndex(editPointer);
            if (pi < 0 || editOnWindow || deck.dragging == null) return;
            int dx = Math.round((e.getX(pi) - editX) / deck.artPx), dy = Math.round((e.getY(pi) - editY) / deck.artPx);
            int[] cur = curEdits().get(deck.dragging);
            if (cur != null && cur[0] == editBase[0] + dx && cur[1] == editBase[1] + dy) return;
            curEdits().put(deck.dragging, new int[]{editBase[0] + dx, editBase[1] + dy, editBase[2]});
            relayoutDeck();
        } else if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_POINTER_UP || act == MotionEvent.ACTION_CANCEL) {
            if (act != MotionEvent.ACTION_CANCEL && e.getPointerId(idx) != editPointer) return;
            editPointer = -1;
            if (editOnWindow) { menuTouchEvent(act, x, y); return; }
            String id = deck.dragging;
            deck.dragging = null;
            if (id != null) {
                Deck.Group g = deck.group(id);
                int[] want = curEdits().get(id);
                if (want == null) want = new int[]{0, 0, 100};
                if (g == null || g.bad || deck.disabled.contains(id)) {
                    // no room right there: the nearest free place, or back to where it was
                    if (editBase[0] == 0 && editBase[1] == 0 && editBase[2] == 100) curEdits().remove(id); else curEdits().put(id, editBase);
                    deck.layout(getWidth(), getHeight());
                    if (!deck.placeNear(getWidth(), getHeight(), id, want, 12)) toast("No room there.");
                } else deck.placeNear(getWidth(), getHeight(), id, want, 0);   // tidies "no change" away
                relayout(); saveEdits(); fillEditMenu();
            }
        }
    }

    /** Lays the deck out again without touching the game picture (while dragging). */
    private void relayoutDeck() {
        deck.layout(getWidth(), getHeight());
        changed();
    }

    // ---------------------------------------------------------------- XP bar
    /** Shows or hides the XP bar (the layout makes room for it). */
    void setXpBar(boolean on) {
        if (deck.xpOn == on) return;
        deck.xpOn = on;
        if (getWidth() > 0) onSizeChanged(getWidth(), getHeight(), getWidth(), getHeight());
        invalidate();
    }

    /** Level, XP and XP needed from the game ({level, xp, next}); emulator thread. Kept while the game shows menus. */
    void xpInfo(final int level, final int xp, final int next) {
        post(new Runnable() { public void run() {
            deck.xpLevel = level; deck.xp = xp; deck.xpNext = next;
            if (deck.xpOn) changed();
        } });
    }

    // ---------------------------------------------------------------- number keys
    private int cursorHeld;   // game key held by the controller's A on the big keypad (0 = none)

    boolean keypadOpen() { return deck.keypadOn; }
    /** Handheld mode with the number keys open: the big keypad, worked with the d-pad and A. */
    boolean bigKeypad() { return deck.bigKeypad; }

    void toggleKeypad() { autoKeypad = false; setKeypad(!deck.keypadOn); }

    /** True while the number keys are open because the game asked for a door code (not by the player). */
    private boolean autoKeypad;

    /** The game started / stopped asking for a door code. Called on the emulator thread. */
    void codePrompt(final boolean on) {
        post(new Runnable() {
            public void run() {
                if (on) {
                    if (!deck.keypadOn) { setKeypad(true); autoKeypad = true; }
                } else if (autoKeypad) {
                    autoKeypad = false;
                    if (deck.keypadOn) setKeypad(false);
                }
            }
        });
    }

    /** Opens / closes the number keys; in handheld mode this switches to / from the big keypad. */
    void setKeypad(boolean on) {
        if (deck.keypadOn == on) return;
        if (on && pickerOn) { pickerOn = false; pickerTouch = -1; }
        cursorPress(false);
        deck.keypadOn = on;
        if (on) { deck.cursor = 4; hidden = false; }      // cursor starts on 5
        if (deck.mode == Deck.MODE_HANDHELD && getWidth() > 0) onSizeChanged(getWidth(), getHeight(), getWidth(), getHeight());
        changed();
    }

    void moveCursor(int dx, int dy) {
        if (!deck.bigKeypad) return;
        cursorPress(false);
        deck.moveCursor(dx, dy);
        changed();
    }

    /** A on the big keypad: presses (down) / releases the key under the cursor. Returns false if nothing was held. */
    boolean cursorPress(boolean down) {
        if (!down) {
            if (cursorHeld == 0) return false;
            keys.release(cursorHeld);
            for (int i = 0; i < deck.keypad.size(); i++) if (deck.keypad.get(i).pointer == -2) deck.keypad.get(i).pointer = -1;
            cursorHeld = 0;
            changed();
            return true;
        }
        if (!deck.bigKeypad || deck.cursor < 0 || deck.cursor >= deck.keypad.size() || cursorHeld != 0) return false;
        Deck.Btn b = deck.keypad.get(deck.cursor);
        haptic();
        if (b.key == Deck.K_CLOSE) { setKeypad(false); return true; }
        b.pointer = -2;           // drawn pressed
        cursorHeld = b.key;
        keys.press(b.key);
        changed();
        return true;
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        for (int i = 0; i < deck.buttons.size(); i++) if (deck.buttons.get(i).pointer != -1 && deck.buttons.get(i).key != 0 && !deck.buttons.get(i).longPress) keys.release(deck.buttons.get(i).key);
        for (int i = 0; i < deck.keypad.size(); i++) if (deck.keypad.get(i).pointer >= 0) keys.release(deck.keypad.get(i).key);
        if (cursorHeld != 0) { keys.release(cursorHeld); cursorHeld = 0; }
        boolean keypad = deck.keypadOn;
        deck.layout(w, h);
        deck.keypadOn = keypad;
        gameRect.set(deck.game[0], deck.game[1], deck.game[2], deck.game[3]);
        // hq / xBR factor: about the size the picture is shown at, 2x to 3x (more costs time and adds little)
        float shown = deck.gW > 0 ? gameRect.width() / deck.gW : 2;
        upFactor = shown < 2.5f ? 2 : 3;
        deckBmp = Bitmap.createBitmap(deck.artW, deck.artH, Bitmap.Config.ARGB_8888);
        deckDst.set(0, 0, deck.artW * deck.artPx, deck.artH * deck.artPx);
        deckDirty = true;
        if (editMenu != null) { if (!canEditTouch()) closeMenu(); else fillEditMenu(); }
    }

    // ---------------------------------------------------------------- drawing
    @Override protected void onDraw(Canvas c) {
        c.drawColor(Color.BLACK);
        if (!hidden && deckBmp != null) {
            if (deckDirty) {
                deck.render();
                deckBmp.setPixels(deck.art.px, 0, deck.artW, 0, 0, deck.artW, deck.artH);
                deckDirty = false;
            }
            c.drawBitmap(deckBmp, null, deckDst, deckPaint);
        }
        synchronized (frameLock) {
            if (front != null) {
                if (bmp == null || bmp.getWidth() != fw || bmp.getHeight() != fh) {
                    bmp = Bitmap.createBitmap(fw, fh, Bitmap.Config.ARGB_8888);
                    dirty = true;
                }
                if (dirty) { bmp.setPixels(front, 0, fw, 0, 0, fw, fh); dirty = false; }
            }
        }
        Bitmap up = null;
        if (upscaler != null && (picture == Prefs.PICTURE_HQ || picture == Prefs.PICTURE_XBR)) {
            up = upscaler.latest(upBmp, picture);
            if (up != null) upBmp = up;
        }
        if (up != null) {
            // the filter's result, scaled the rest of the way smoothly
            src.set(0, 0, up.getWidth(), up.getHeight());
            bmpPaint.setFilterBitmap(true);
            c.drawBitmap(up, src, gameRect, bmpPaint);
        } else if (bmp != null) {
            src.set(0, 0, bmp.getWidth(), bmp.getHeight());
            // Smooth, or the big keypad (it shrinks the game to an uneven size), or a filter's first frame not ready yet
            bmpPaint.setFilterBitmap(picture != Prefs.PICTURE_SHARP || deck.bigKeypad);
            c.drawBitmap(bmp, src, gameRect, bmpPaint);
        }
        drawMap(c);
        drawPicker(c);
        drawMenu(c);
        drawTour(c);
        drawToast(c);
    }

    // ---------------------------------------------------------------- pixel windows (settings, questions, notes)
    private final java.util.ArrayList<PixelMenu> menus = new java.util.ArrayList<PixelMenu>();
    private boolean menuController, menuDirty;
    private Bitmap menuBmp;
    private final RectF menuDst = new RectF();
    private float menuScale;
    private int menuTouch = -2;

    boolean menuOpen() { return !menus.isEmpty(); }
    PixelMenu topMenu() { return menus.isEmpty() ? null : menus.get(menus.size() - 1); }

    /** Opens a window on top of any open one (controller: the cursor starts on the first row). */
    void openMenu(PixelMenu m, boolean controller) {
        if (pickerOn) setPicker(false, false);
        if (deck.keypadOn) setKeypad(false);
        releaseTouches();
        menuController = controller;
        menuFollow = true;
        if (m.cursor < 0) m.firstSelectable();
        menus.add(m);
        menuDirty = true; invalidate();
    }

    /** Closes the top window (runs its onClose). */
    void closeMenu() {
        if (menus.isEmpty()) return;
        PixelMenu m = menus.remove(menus.size() - 1);
        menuDirty = true; invalidate();
        if (m.onClose != null) m.onClose.run();
    }

    /** Closes every window without running their onClose. */
    /** Lets go of deck buttons held by fingers (their release goes to the window that just opened). */
    private void releaseTouches() {
        for (int i = 0; i < deck.buttons.size(); i++) {
            Deck.Btn b = deck.buttons.get(i);
            if (b.pointer < 0) continue;
            if (!b.longPress && b.key != 0 && b.key != K_SETTINGS && b.key != K_WEAPONS) keys.release(b.key);
            b.pointer = -1;
        }
        for (int i = 0; i < deck.keypad.size(); i++) { Deck.Btn b = deck.keypad.get(i); if (b.pointer >= 0) { keys.release(b.key); b.pointer = -1; } }
        swPointer = -1; swKey = 0;
        changed();
    }

    void closeAllMenus() { menus.clear(); menuDirty = true; invalidate(); }

    /** The top window's rows changed (call after changing its items). */
    void menuChanged() { menuDirty = true; invalidate(); }

    void menuMove(int d) { PixelMenu m = topMenu(); if (m == null) return; menuController = true; menuFollow = true; m.move(d); haptic(); menuChanged(); }

    /** A (or a tap): the row's action. */
    void menuActivate() {
        PixelMenu m = topMenu();
        if (m == null || m.cursor < 0 || m.cursor >= m.items.size()) return;
        PixelMenu.Item it = m.items.get(m.cursor);
        if (!it.selectable()) return;
        haptic();
        if (it.act != null) it.act.run();
        menuChanged();
    }

    /** Left / right on a row that has a value. */
    void menuSide(int d) {
        PixelMenu m = topMenu();
        if (m == null || m.cursor < 0) return;
        PixelMenu.Item it = m.items.get(m.cursor);
        Runnable r = d < 0 ? it.left : it.right;
        if (r == null && (it.kind == PixelMenu.SWITCH)) r = it.act;
        if (r != null) { haptic(); r.run(); menuChanged(); }
    }

    private void drawMenu(Canvas c) {
        PixelMenu m = topMenu();
        if (m == null) return;
        if (menuDirty || menuBmp == null) {
            PixelArt a = m.paint(menuController);
            if (menuBmp == null || menuBmp.getWidth() != a.w || menuBmp.getHeight() != a.h) menuBmp = Bitmap.createBitmap(a.w, a.h, Bitmap.Config.ARGB_8888);
            menuBmp.setPixels(a.px, 0, a.w, 0, 0, a.w, a.h);
            menuDirty = false;
        }
        float s, w, h, x, y;
        if (m.editor) {
            // the editor: over the game picture only, so the whole deck stays free to drag
            c.drawRect(gameRect, dimPaint); c.drawRect(gameRect, dimPaint);
            s = fitScale(gameRect.width() * 0.98f / menuBmp.getWidth(), gameRect.height() * 0.96f / menuBmp.getHeight());
            w = menuBmp.getWidth() * s; h = menuBmp.getHeight() * s;
            x = gameRect.centerX() - w / 2; y = gameRect.centerY() - h / 2;
        } else {
            c.drawRect(0, 0, getWidth(), getHeight(), dimPaint);
            // sized to the screen: big enough to read on this screen (as wide as fits); a window taller than the
            // screen shows part of its rows and scrolls (drag, or the controller cursor)
            int bw = menuBmp.getWidth(), bh = menuBmp.getHeight();
            s = fitScale(getWidth() * 0.96f / bw, readScale());
            int avail = (int) (getHeight() * 0.96f / s);
            menuBodyTop = m.bodyTop(); int end = m.bodyEnd(), foot = bh - end;
            menuBodyVis = end - menuBodyTop; menuScrollMax = 0;
            if (bh > avail) {
                int vis = avail - menuBodyTop - foot;
                if (vis >= 40) { menuBodyVis = vis; menuScrollMax = end - menuBodyTop - vis; }
                else { s = fitScale(getWidth() * 0.96f / bw, getHeight() * 0.96f / bh); }   // no room to scroll: all of it, smaller
            }
            if (menuFollow && m.cursor >= 0 && m.cursor < m.items.size()) {   // keep the controller's row in view
                int rt = m.rowTop(m.cursor) - menuBodyTop, rb = rt + m.rowH(m.items.get(m.cursor));
                if (rt - 3 < m.scroll) m.scroll = rt - 3;
                if (rb + 3 > m.scroll + menuBodyVis) m.scroll = rb + 3 - menuBodyVis;
            }
            m.scroll = Math.max(0, Math.min(menuScrollMax, m.scroll));
            menuFullH = bh; menuVisH = bh - (end - menuBodyTop - menuBodyVis);
            w = bw * s; h = menuVisH * s;
            float cy = deck.mode == Deck.MODE_TOUCH && getHeight() > getWidth() ? gameRect.centerY() : getHeight() / 2f;
            x = (getWidth() - w) / 2; y = Math.max(0, Math.min(getHeight() - h, cy - h / 2));
        }
        menuScale = s;
        menuDst.set(x, y, x + w, y + h);
        if (m.editor || menuScrollMax <= 0 && menuVisH == menuFullH) {
            if (m.editor) { menuScrollMax = 0; menuBodyTop = 0; menuBodyVis = menuBmp.getHeight(); menuFullH = menuVisH = menuBmp.getHeight(); m.scroll = 0; }
            c.drawBitmap(menuBmp, null, menuDst, pickerPaint);
        } else {
            // three pieces: the title and message, the rows (scrolled), the hint at the bottom
            int top = menuBodyTop, vis = menuBodyVis, foot = menuFullH - (top + vis + menuScrollMax);
            tmpSrc.set(0, 0, menuBmp.getWidth(), top); tmpDst.set(x, y, x + w, y + top * s);
            c.drawBitmap(menuBmp, tmpSrc, tmpDst, pickerPaint);
            tmpSrc.set(0, top + m.scroll, menuBmp.getWidth(), top + m.scroll + vis); tmpDst.set(x, y + top * s, x + w, y + (top + vis) * s);
            c.drawBitmap(menuBmp, tmpSrc, tmpDst, pickerPaint);
            tmpSrc.set(0, menuFullH - foot, menuBmp.getWidth(), menuFullH); tmpDst.set(x, y + (top + vis) * s, x + w, y + h);
            c.drawBitmap(menuBmp, tmpSrc, tmpDst, pickerPaint);
            // scroll bar on the right edge
            float bx = x + w - 3 * s, by0 = y + top * s, bh2 = vis * s;
            float th = Math.max(6 * s, bh2 * vis / (float) (vis + menuScrollMax)), ty = by0 + (bh2 - th) * m.scroll / (float) menuScrollMax;
            scrollPaint.setColor(0xFF282321); c.drawRect(bx, by0, bx + 2 * s, by0 + bh2, scrollPaint);
            scrollPaint.setColor(0xFFF48C28); c.drawRect(bx, ty, bx + 2 * s, ty + th, scrollPaint);
        }
        // pictures on RADIO rows (the Picture choice): a piece of the game as each choice shows it
        for (int i = 0; i < m.items.size(); i++) {
            PixelMenu.Item it = m.items.get(i);
            if (it.preview < 0 || it.preview >= previews.length || previews[it.preview] == null) continue;
            int[] r = m.previewRect(i);
            int dy = r[1] >= menuBodyTop ? -m.scroll : 0;
            if (r[1] + dy < menuBodyTop || r[3] + dy >= menuBodyTop + menuBodyVis) continue;   // scrolled out of view
            tmpDst.set(x + r[0] * s, y + (r[1] + dy) * s, x + (r[2] + 1) * s, y + (r[3] + 1 + dy) * s);
            pickerPaint.setFilterBitmap(it.preview != Prefs.PICTURE_SHARP);
            c.drawBitmap(previews[it.preview], null, tmpDst, pickerPaint);
            pickerPaint.setFilterBitmap(false);
        }
    }

    /**
     * Pixel size for a window: as big as fits (sized to the screen, not to the deck, so small handheld screens get
     * readable windows). Whole pixels from 3x up keep the pixel art even; below that the exact fit is used, as
     * every bit of size counts there.
     */
    private static float fitScale(float a, float b) {
        float s = Math.min(a, b);
        if (s >= 3) return (float) Math.floor(s);
        return Math.max(1f, s);
    }

    // a scrolling window: what is shown (art pixels)
    private int menuBodyTop, menuBodyVis, menuScrollMax, menuFullH, menuVisH;
    /** The controller moved the cursor: keep its row in view. */
    private boolean menuFollow = true;
    private float menuDragY; private int menuDragScroll; private boolean menuDragging;
    private final Rect tmpSrc = new Rect();
    private final Paint scrollPaint = new Paint();

    /** Window art y under screen y (the rows may be scrolled). */
    private float menuArtY(PixelMenu m, float y, float s) {
        float ya = (y - menuDst.top) / s;
        if (ya < menuBodyTop) return ya;
        if (ya < menuBodyTop + menuBodyVis) return ya + m.scroll;
        return ya + (menuFullH - menuVisH);
    }

    /** A pixel scale that makes the 5-pixel font about 2.4 mm tall on this screen (8x on a typical phone). */
    private float readScale() { return 3.0f * dp; }

    private boolean menuTouchEvent(int act, float x, float y) {
        PixelMenu m = topMenu();
        if (m == null) return false;
        float s = menuScale > 0 ? menuScale : deck.artPx;
        int row = menuDst.contains(x, y) ? m.itemAt((x - menuDst.left) / s, menuArtY(m, y, s)) : -1;
        if (act == MotionEvent.ACTION_MOVE) {
            // drag to scroll a long window
            if (menuScrollMax <= 0 || menuTouch == -3 || menuTouch == -2 && !menuDragging) return true;
            if (!menuDragging && Math.abs(y - menuDragY) > 8 * dp) menuDragging = true;
            if (menuDragging) {
                menuFollow = false;
                m.scroll = Math.max(0, Math.min(menuScrollMax, menuDragScroll - Math.round((y - menuDragY) / s)));
                invalidate();
            }
            return true;
        }
        if (act == MotionEvent.ACTION_DOWN || act == MotionEvent.ACTION_POINTER_DOWN) {
            menuDragY = y; menuDragScroll = m.scroll; menuDragging = false; menuFollow = false;
            menuTouch = menuDst.contains(x, y) ? row : -3;
            if (menuTouch == -2) menuTouch = -1;   // on the window, not on a row: may still drag
            if (row >= 0) { m.cursor = row; menuController = false; menuChanged(); }
        } else if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_POINTER_UP) {
            int arrow = row >= 0 ? m.arrowAt(row, (x - menuDst.left) / s) : 0;
            if (menuDragging) { menuDragging = false; menuTouch = -2; return true; }   // it was a scroll, not a tap
            if (menuTouch >= 0 && row == menuTouch && arrow != 0) { m.cursor = row; menuSide(arrow); }   // tapped "<" or ">"
            else if (menuTouch >= 0 && row == menuTouch) menuActivate();
            else if (menuTouch == -3 && !menuDst.contains(x, y) && !m.editor) closeMenu();
            menuTouch = -2;
        } else if (act == MotionEvent.ACTION_CANCEL) menuTouch = -2;
        return true;
    }

    // picture previews for the Picture choice: the middle of the current frame, as each picture setting shows it
    final Bitmap[] previews = new Bitmap[4];

    void makePreviews() {
        int[] f; int w, h;
        synchronized (frameLock) { if (front == null) return; f = front.clone(); w = fw; h = fh; }
        int pw = Math.min(w, 34), ph = Math.min(h, 14), x0 = (w - pw) / 2, y0 = Math.max(0, h * 2 / 5 - ph / 2);
        int[] crop = new int[pw * ph];
        for (int y = 0; y < ph; y++) System.arraycopy(f, (y0 + y) * w + x0, crop, y * pw, pw);
        previews[Prefs.PICTURE_SHARP] = Bitmap.createBitmap(crop, pw, ph, Bitmap.Config.ARGB_8888);
        previews[Prefs.PICTURE_SMOOTH] = previews[Prefs.PICTURE_SHARP];
        int[] dst = new int[pw * 4 * ph * 4], yuv = new int[pw * ph];
        brewemu.video.Hqx.scale(crop, pw, ph, 4, dst, yuv);
        previews[Prefs.PICTURE_HQ] = Bitmap.createBitmap(dst, pw * 4, ph * 4, Bitmap.Config.ARGB_8888);
        int[] dst2 = new int[pw * 4 * ph * 4];
        brewemu.video.Xbr.scale(crop, pw, ph, 4, dst2, yuv);
        previews[Prefs.PICTURE_XBR] = Bitmap.createBitmap(dst2, pw * 4, ph * 4, Bitmap.Config.ARGB_8888);
    }

    // ---------------------------------------------------------------- pixel notes (a short message at the top)
    private String toastText;
    private long toastUntil;
    private Bitmap toastBmp;

    void toast(String text) {
        toastText = text.toUpperCase(); toastUntil = SystemClock.uptimeMillis() + 3200; toastBmp = null;
        invalidate();
        postDelayed(new Runnable() { public void run() { invalidate(); } }, 3300);
    }

    private void drawToast(Canvas c) {
        if (toastText == null || SystemClock.uptimeMillis() > toastUntil) return;
        if (toastBmp == null) {
            PixelMenu m = new PixelMenu(); m.say(toastText);
            int lines = m.message.size(), H = lines * 7 + 8;
            PixelArt a = new PixelArt(PixelMenu.W + 2, H + 2);
            a.bevelBox(1, 1, PixelMenu.W, H, PixelArt.D3, PixelArt.OR, PixelArt.R1, false);
            int y = 5;
            for (String l : m.message) { a.text(l, 1 + PixelMenu.W / 2 - PixelArt.textWidth(l, 1) / 2, y, PixelArt.BONE, 1); y += 7; }
            toastBmp = Bitmap.createBitmap(a.px, a.w, a.h, Bitmap.Config.ARGB_8888);
        }
        // a note: as big as the deck's pixels, or a size that suits the screen (small handheld screens)
        float s = fitScale(getWidth() * 0.9f / toastBmp.getWidth(), readScale());
        float w = toastBmp.getWidth() * s, h = toastBmp.getHeight() * s, x = (getWidth() - w) / 2, y = gameRect.top + gameRect.height() * 0.08f;
        tmpDst.set(x, y, x + w, y + h);
        c.drawBitmap(toastBmp, null, tmpDst, pickerPaint);
    }

    // ---------------------------------------------------------------- first-run tour (touch controls)
    private int tourStep = -1;
    private Runnable tourDone;
    private static final String[][] TOUR = {
        {"WALK AND TURN", "UP / DOWN: STEP. LEFT / RIGHT: TURN. HOLD TO KEEP GOING."},
        {"SIDE-STEP", "L AND R STEP SIDEWAYS. THE HOURGLASS WAITS A TURN."},
        {"FIRE / USE", "ATTACK, OPEN DOORS, TALK AND PICK THINGS UP."},
        {"WEAPONS", "ARROWS: PREVIOUS / NEXT. TAP THE GUN FOR ALL YOUR WEAPONS."},
        {"MENU AND SETTINGS", "THREE BARS: THE GAME'S MENU (SAVE GAME IS THERE). GEAR: SETTINGS AND HELP."},
    };

    boolean tourActive() { return tourStep >= 0; }

    /** The tour is for the touch layout (not the handheld one). */
    boolean touchTourAvailable() { return deck.mode == Deck.MODE_TOUCH && !deck.handheldLayout; }

    /** Shows the short first-run tour (touch layout only). */
    void startTour(Runnable done) {
        if (deck.mode != Deck.MODE_TOUCH || deck.handheldLayout) { if (done != null) done.run(); return; }
        releaseTouches();
        tourStep = 0; tourDone = done; invalidate();
    }

    void tourNext(boolean skip) {
        tourStep = skip ? TOUR.length : tourStep + 1;
        if (tourStep >= TOUR.length) { tourStep = -1; Runnable d = tourDone; tourDone = null; if (d != null) d.run(); }
        invalidate();
    }

    /** Screen rectangle (art pixels: x0, y0, x1, y1) of what the tour step points at. */
    private int[] tourTarget(int step) {
        int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE;
        for (int i = 0; i < deck.buttons.size(); i++) {
            Deck.Btn b = deck.buttons.get(i);
            boolean hit;
            switch (step) {
                case 0: hit = b.kind == Deck.CELL; break;
                case 1: hit = b.kind == Deck.SHOULDER || b.key == K_WAIT; break;
                case 2: hit = b.kind == Deck.FIRE; break;
                case 3: hit = b.kind == Deck.SLOT; break;
                default: hit = b.key == K_MENU || b.key == K_SETTINGS; break;
            }
            if (!hit) continue;
            int bx0, by0, bx1, by1;
            if (b.kind == Deck.DISC || b.kind == Deck.FIRE) { bx0 = b.cx - b.r - 1; by0 = b.cy - b.r - 1; bx1 = b.cx + b.r + 1; by1 = b.cy + b.r + 1; }
            else { bx0 = b.x0; by0 = b.y0; bx1 = b.x1; by1 = b.y1; }
            x0 = Math.min(x0, bx0); y0 = Math.min(y0, by0); x1 = Math.max(x1, bx1); y1 = Math.max(y1, by1);
        }
        return x0 == Integer.MAX_VALUE ? null : new int[]{x0 - 2, y0 - 2, x1 + 2, y1 + 2};
    }

    private final Paint tourPaint = new Paint();
    private void drawTour(Canvas c) {
        if (tourStep < 0) return;
        int[] t = tourTarget(tourStep);
        float a = deck.artPx;
        tourPaint.setColor(0xB0000000); tourPaint.setStyle(Paint.Style.FILL);
        if (t != null) {
            float x0 = t[0] * a, y0 = t[1] * a, x1 = (t[2] + 1) * a, y1 = (t[3] + 1) * a;
            c.drawRect(0, 0, getWidth(), y0, tourPaint); c.drawRect(0, y1, getWidth(), getHeight(), tourPaint);
            c.drawRect(0, y0, x0, y1, tourPaint); c.drawRect(x1, y0, getWidth(), y1, tourPaint);
            tourPaint.setStyle(Paint.Style.STROKE); tourPaint.setStrokeWidth(Math.max(2, a * 0.75f)); tourPaint.setColor(0xFFF48C28);
            c.drawRect(x0, y0, x1, y1, tourPaint);
        } else c.drawRect(0, 0, getWidth(), getHeight(), tourPaint);
        if (tourBmp == null || tourBmpStep != tourStep) {
            PixelMenu m = new PixelMenu();
            m.title = TOUR[tourStep][0]; m.corner = (tourStep + 1) + "/" + TOUR.length;
            m.say(TOUR[tourStep][1]);
            m.hintTouch = "TAP: NEXT    SKIP: TAP HERE";
            PixelArt art = m.paint(false);
            tourBmp = Bitmap.createBitmap(art.px, art.w, art.h, Bitmap.Config.ARGB_8888);
            tourBmpStep = tourStep;
        }
        Bitmap b = tourBmp;
        float s = Math.min(a, getWidth() * 0.94f / b.getWidth());
        if (s >= 2) s = (float) Math.floor(s);
        float w = b.getWidth() * s, h = b.getHeight() * s, x = (getWidth() - w) / 2;
        float y = t != null && t[1] * a > getHeight() / 2f ? Math.max(0, t[1] * a - h - a * 4) : gameRect.centerY() - h / 2;
        if (t != null && y < gameRect.top) y = gameRect.top + a * 4;
        tourBox.set(x, y, x + w, y + h);
        c.drawBitmap(b, null, tourBox, pickerPaint);
    }
    private final RectF tourBox = new RectF();
    private Bitmap tourBmp;
    private int tourBmpStep = -1;

    // ---------------------------------------------------------------- weapons: the slot and the picker
    private final WeaponPicker picker = new WeaponPicker();
    private boolean pickerOn, pickerController, pickerDirty;
    private boolean weaponsKnown;
    private Bitmap pickerBmp;
    private final RectF pickerDst = new RectF();
    private float pickerScale;
    private int pickerTouch = -1;   // cell held by a finger
    private final Paint pickerPaint = new Paint(), dimPaint = new Paint();
    /** Weapon pictures (from the game file where it has them), normal and greyed; ammo icons by ammo type. */
    private final Bitmap[] wpnPic = new Bitmap[12], wpnGrey = new Bitmap[12], ammoPic = new Bitmap[5];
    { pickerPaint.setFilterBitmap(false); dimPaint.setColor(0x99000000); }

    /** Pictures for the picker; call on the UI thread once they are decoded (null = use the simple icons). */
    void setWeaponArt(brewemu.doomrpg.DoomArt art) {
        for (int w = 0; w < 12; w++) {
            int[] px; int pw, ph;
            brewemu.doomrpg.DoomArt.Pic p = art != null ? art.weapons[w] : null;
            if (w == 2) { px = WeaponArt.pistol(); pw = WeaponArt.PISTOL_W; ph = WeaponArt.PISTOL_H; }
            else if (p != null) { px = p.px; pw = p.w; ph = p.h; }
            else { String[] ic = Icons.WEAPON[w]; pw = ic[0].length(); ph = ic.length; px = WeaponArt.icon(ic, PixelArt.BONE); }
            wpnPic[w] = Bitmap.createBitmap(px, pw, ph, Bitmap.Config.ARGB_8888);
            wpnGrey[w] = Bitmap.createBitmap(WeaponArt.grey(px), pw, ph, Bitmap.Config.ARGB_8888);
        }
        for (int t = 0; t < 5; t++) {
            brewemu.doomrpg.DoomArt.Pic p = art != null ? art.ammo[t] : null;
            ammoPic[t] = p != null ? Bitmap.createBitmap(p.px, p.w, p.h, Bitmap.Config.ARGB_8888) : null;
        }
        pickerDirty = true; invalidate();
    }

    /** The player's weapons from the game (null = not playing right now). Called on the emulator thread. */
    void weaponInfo(final brewemu.doomrpg.DoomWeapons wi) {
        final boolean known = wi != null;
        final int owned = known ? wi.owned : 0, weapon = known ? wi.weapon : -1, prev = known ? wi.prev() : -1, next = known ? wi.next() : -1;
        final int[] ammo = known ? wi.ammo.clone() : null;
        post(new Runnable() {
            public void run() {
                weaponsKnown = known;
                deck.wpnCur = weapon; deck.wpnPrev = prev == weapon ? -1 : prev; deck.wpnNext = next == weapon ? -1 : next;
                if (known) {
                    picker.owned = owned; picker.weapon = weapon;
                    System.arraycopy(ammo, 0, picker.ammo, 0, 6);
                    if (picker.cursor >= picker.cells()) picker.cursor = 0;
                    pickerDirty = true;
                } else if (pickerOn) setPicker(false, false);
                changed();
            }
        });
    }

    boolean pickerOpen() { return pickerOn; }

    /** Opens / closes the weapon picker (only while the game is being played). */
    void setPicker(boolean on, boolean controller) {
        if (on && (!weaponsKnown || deck.bigKeypad || !menus.isEmpty() || tourStep >= 0)) return;
        if (on && deck.keypadOn) setKeypad(false);
        pickerOn = on; pickerController = controller; pickerTouch = -1;
        if (on) picker.cursor = Math.max(0, picker.weapon);
        pickerDirty = true; invalidate();
    }

    void pickerMove(int dx, int dy) { if (!pickerOn) return; picker.moveCursor(dx, dy); pickerController = true; pickerDirty = true; haptic(); invalidate(); }

    /** Takes the weapon under the cursor (if the game allows it) and closes the picker. */
    void pickerChoose() {
        if (!pickerOn) return;
        int w = picker.weaponAt(picker.cursor);
        if (w < 0 || !picker.usable(w)) { pickerDirty = true; invalidate(); return; }
        haptic();
        if (w != picker.weapon) keys.selectWeapon(w);
        setPicker(false, false);
    }

    private void drawPicker(Canvas c) {
        if (!pickerOn) return;
        if (pickerDirty || pickerBmp == null) {
            PixelArt a = picker.paint(pickerController);
            if (pickerBmp == null || pickerBmp.getWidth() != a.w || pickerBmp.getHeight() != a.h) pickerBmp = Bitmap.createBitmap(a.w, a.h, Bitmap.Config.ARGB_8888);
            pickerBmp.setPixels(a.px, 0, a.w, 0, 0, a.w, a.h);
            pickerDirty = false;
        }
        c.drawRect(gameRect, dimPaint);
        // the window's pixel size: as big as fits the screen
        float s = fitScale(Math.min(getWidth() * 0.96f / pickerBmp.getWidth(), getHeight() * 0.96f / pickerBmp.getHeight()), readScale());
        pickerScale = s;
        float w = pickerBmp.getWidth() * s, h = pickerBmp.getHeight() * s;
        float x = Math.max(0, Math.min(getWidth() - w, gameRect.centerX() - w / 2)), y = Math.max(0, Math.min(getHeight() - h, gameRect.centerY() - h / 2));
        pickerDst.set(x, y, x + w, y + h);
        c.drawBitmap(pickerBmp, null, pickerDst, pickerPaint);
        for (int i = 0; i < picker.cells(); i++) {
            int wpn = picker.weaponAt(i);
            if (wpn < 0 || !picker.owns(wpn) || wpnPic[wpn] == null) continue;
            Bitmap b = picker.usable(wpn) ? wpnPic[wpn] : wpnGrey[wpn];
            int[] r = picker.picRect(i);
            float rw = (r[2] - r[0] + 1) * s, rh = (r[3] - r[1] + 1) * s;
            // the pistol drawing is sized like the long guns' pickups (64 x 17), so it looks as small as a pistol is
            float k = wpn == 2 ? Math.min(rw / 64f, rh / 17f) : Math.min(rw / b.getWidth(), rh / b.getHeight());
            if (k >= 1) k = (float) Math.floor(k);
            float bw = b.getWidth() * k, bh = b.getHeight() * k, bx = x + r[0] * s + (rw - bw) / 2, by = y + r[1] * s + (rh - bh) / 2;
            tmpDst.set(bx, by, bx + bw, by + bh);
            pickerPaint.setFilterBitmap(k < 1);
            c.drawBitmap(b, null, tmpDst, pickerPaint);
            pickerPaint.setFilterBitmap(false);
            if (wpn < 9 && brewemu.doomrpg.DoomWeapons.AMMO_USE[wpn] > 0) {
                Bitmap ab = ammoPic[brewemu.doomrpg.DoomWeapons.AMMO_TYPE[wpn]];
                if (ab == null) continue;
                int[] p = picker.ammoIconAt(i);
                float ak = Math.max(1, (float) Math.floor(8 * s / ab.getHeight()));
                float aw = ab.getWidth() * ak, ah = ab.getHeight() * ak, ax = x + p[0] * s - aw, ay = y + p[1] * s - ah / 2;
                tmpDst.set(ax, ay, ax + aw, ay + ah);
                c.drawBitmap(ab, null, tmpDst, pickerPaint);
            }
        }
    }
    private final RectF tmpDst = new RectF();

    /** Touch while the picker is open: a tap on a weapon takes it, a tap outside closes. Returns true if handled. */
    private boolean pickerTouch(int act, int id, float x, float y) {
        if (!pickerOn) return false;
        float s = pickerScale > 0 ? pickerScale : deck.artPx;
        int cell = pickerDst.contains(x, y) ? picker.cellAt((x - pickerDst.left) / s, (y - pickerDst.top) / s) : -1;
        if (act == MotionEvent.ACTION_DOWN || act == MotionEvent.ACTION_POINTER_DOWN) {
            pickerTouch = cell;
            if (cell >= 0) { picker.cursor = cell; pickerController = false; pickerDirty = true; invalidate(); }
        } else if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_POINTER_UP) {
            if (pickerTouch >= 0 && cell == pickerTouch) pickerChoose();
            else if (pickerTouch < 0 && !pickerDst.contains(x, y)) setPicker(false, false);
            pickerTouch = -1;
        } else if (act == MotionEvent.ACTION_CANCEL) pickerTouch = -1;
        return true;
    }

    private void drawMap(Canvas c) {
        int size, gw;
        synchronized (frameLock) {
            size = mapSize; gw = fw;
            if (size == 0 || gw == 0 || deck.bigKeypad) return;
            if (mapBmp == null || mapBmp.getWidth() != size) { mapBmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888); mapDirty = true; }
            if (mapDirty) { mapBmp.setPixels(mapFront, 0, size, 0, 0, size, size); mapDirty = false; }
        }
        // in game pixels: 3 from the right edge, just below the game's message bar at the top
        float s = gameRect.width() / gw;
        mapPaint.setAlpha(mapBig ? 165 : 210);
        mapDst.set(gameRect.right - (size + 3) * s, gameRect.top + 21 * s, gameRect.right - 3 * s, gameRect.top + (21 + size) * s);
        c.drawBitmap(mapBmp, null, mapDst, mapPaint);
    }

    // ---------------------------------------------------------------- input
    /** Button-tap feedback for this app only; follows the app's Vibration setting, never changes phone settings. */
    private void haptic() { if (Prefs.vibrate(getContext())) performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); }
    void hapticTap() { haptic(); }

    /** Hides the touch deck while a controller is used (touch mode only; handheld panels always show). */
    void setHidden(boolean h) {
        if (h && deck.mode != Deck.MODE_TOUCH) return;
        if (hidden != h) { hidden = h; invalidate(); }
    }

    private void changed() { deckDirty = true; invalidate(); }

    /** A tap on the game's own bottom bar presses its labels (left: Menu/Back, right: Map/Leave); elsewhere it fires. */
    private int softKeyAt(float x, float y) {
        float gx = (x - gameRect.left) / gameRect.width();
        float gy = (y - gameRect.top) / gameRect.height();
        if (gy >= 1f - 0.07f) {
            if (gx < 1f / 3) return K_MENU;
            if (gx > 2f / 3) return K_MAP;
        }
        return K_FIRE;
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        setHidden(false);
        int act = e.getActionMasked();
        int idx = e.getActionIndex();
        if (tourStep >= 0) {
            if (act == MotionEvent.ACTION_UP) {
                // the hint line at the bottom of the box skips the rest
                boolean skip = tourBox.contains(e.getX(idx), e.getY(idx)) && e.getY(idx) > tourBox.bottom - tourBox.height() * 0.2f;
                haptic(); tourNext(skip);
            }
            return true;
        }
        if (!menus.isEmpty() && topMenu().editor) { editorTouch(e); return true; }
        if (!menus.isEmpty()) {
            if (act == MotionEvent.ACTION_MOVE) { if (e.getPointerCount() > 0) menuTouchEvent(act, e.getX(0), e.getY(0)); }
            else menuTouchEvent(act, e.getX(idx), e.getY(idx));
            return true;
        }
        if (pickerOn && act != MotionEvent.ACTION_MOVE) { pickerTouch(act, e.getPointerId(idx), e.getX(idx), e.getY(idx)); return true; }
        if (pickerOn) return true;
        switch (act) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                down(e.getPointerId(idx), e.getX(idx), e.getY(idx));
                break;
            case MotionEvent.ACTION_MOVE:
                for (int i = 0; i < e.getPointerCount(); i++) move(e.getPointerId(i), e.getX(i), e.getY(i));
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                up(e.getPointerId(idx), false);
                break;
            case MotionEvent.ACTION_CANCEL:
                for (int i = 0; i < e.getPointerCount(); i++) up(e.getPointerId(i), true);
                break;
        }
        return true;
    }

    private void down(int id, float x, float y) {
        long now = SystemClock.uptimeMillis();
        float ax = x / deck.artPx, ay = y / deck.artPx;
        if (deck.keypadOn) {
            for (int i = 0; i < deck.keypad.size(); i++) {
                Deck.Btn b = deck.keypad.get(i);
                if (!b.hit(ax, ay, 0)) continue;
                haptic();
                if (b.key == Deck.K_CLOSE) { setKeypad(false); return; }
                b.pointer = id; keys.press(b.key); changed(); return;
            }
        }
        if (!gameRect.contains(x, y)) {
            for (int i = 0; i < deck.buttons.size(); i++) {
                Deck.Btn b = deck.buttons.get(i);
                if (deck.blocked(b) || !b.hit(ax, ay, 2)) continue;
                b.pointer = id; b.downAt = now; b.lastRepeat = now; b.longFired = false;
                haptic();
                if (b.key == 0) { b.pointer = -1; setKeypad(!deck.keypadOn); return; }
                if (!b.longPress) keys.press(b.key);
                changed();
                return;
            }
        }
        if (gameRect.contains(x, y) && swPointer == -1 && !deck.bigKeypad) {   // no firing by accident while typing a code
            swPointer = id; swX = x; swY = y; swAt = now; swDone = false; swKey = 0;
        }
    }

    private void move(int id, float x, float y) {
        // sliding a finger across the movement block switches buttons
        for (int i = 0; i < deck.buttons.size(); i++) {
            Deck.Btn b = deck.buttons.get(i);
            if (b.pointer != id || b.kind != Deck.CELL) continue;
            float ax = x / deck.artPx, ay = y / deck.artPx;
            if (b.hit(ax, ay, 1)) return;
            for (int j = 0; j < deck.buttons.size(); j++) {
                Deck.Btn n = deck.buttons.get(j);
                if (n.kind == Deck.CELL && n != b && n.hit(ax, ay, 0)) {
                    keys.release(b.key); b.pointer = -1;
                    long now = SystemClock.uptimeMillis();
                    n.pointer = id; n.downAt = now; n.lastRepeat = now;
                    keys.press(n.key); haptic(); changed();
                    return;
                }
            }
            return;
        }
        if (id == swPointer && !swDone) {
            float dx = x - swX, dy = y - swY, th = 28 * dp;
            if (Math.abs(dx) > th || Math.abs(dy) > th) {
                if (Math.abs(dx) > Math.abs(dy)) swKey = dx > 0 ? K_RIGHT : K_LEFT;
                else swKey = dy > 0 ? K_DOWN : K_UP;
                swDone = true;
                swLastRepeat = SystemClock.uptimeMillis();
                keys.tap(swKey);
                haptic();
            }
        }
    }

    private void up(int id, boolean cancel) {
        boolean any = false;
        for (int i = 0; i < deck.keypad.size(); i++) {
            Deck.Btn b = deck.keypad.get(i);
            if (b.pointer == id) { b.pointer = -1; keys.release(b.key); any = true; }
        }
        for (int i = 0; i < deck.buttons.size(); i++) {
            Deck.Btn b = deck.buttons.get(i);
            if (b.pointer == id) {
                b.pointer = -1; any = true;
                if (b.longPress) { if (!b.longFired && !cancel) keys.tap(b.key); }
                else if (b.key == K_SETTINGS) { if (!cancel && settings != null) settings.run(); }
                else if (b.key == K_WEAPONS) { if (!cancel) setPicker(!pickerOn, false); }
                else if (b.key != 0) keys.release(b.key);
            }
        }
        if (any) changed();
        if (id == swPointer) {
            swPointer = -1;
            if (!cancel && !swDone && SystemClock.uptimeMillis() - swAt < 350) { keys.tap(softKeyAt(swX, swY)); haptic(); }
            swKey = 0;
        }
    }

    // auto-repeat for held movement controls, long-press for the menu button
    private final Runnable ticker = new Runnable() {
        public void run() {
            long now = SystemClock.uptimeMillis();
            final long delay = 320, rate = 170;
            for (int i = 0; i < deck.buttons.size(); i++) {
                Deck.Btn b = deck.buttons.get(i);
                if (b.repeat && b.pointer != -1 && now - b.downAt > delay && now - b.lastRepeat > rate) {
                    b.lastRepeat = now; keys.release(b.key); keys.press(b.key);
                }
                if (b.longPress && b.pointer != -1 && !b.longFired && now - b.downAt > 700) {
                    b.longFired = true; b.pointer = -1;
                    if (Prefs.vibrate(getContext())) performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    changed();
                    if (settings != null) settings.run();
                }
            }
            // the keypad toggle pulses while the number keys are showing
            if (deck.keypadOn && now - lastPulse > 450) { lastPulse = now; deck.pulse = !deck.pulse; changed(); }
            // swipe and hold keeps walking / turning
            if (swPointer != -1 && swDone && swKey != 0 && now - swLastRepeat > delay + rate) {
                swLastRepeat = now - delay; keys.tap(swKey);
            }
            postDelayed(this, 30);
        }
    };
}
